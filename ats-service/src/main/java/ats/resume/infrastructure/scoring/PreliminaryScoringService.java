package ats.resume.infrastructure.scoring;

import ats.resume.domain.ingestion.HhApplicationScoredEvent;
import ats.resume.domain.scoring.ParsedResume;
import ats.resume.domain.scoring.ScoringClient;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.VacancyClient;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import ats.resume.infrastructure.persistence.entity.ResumeExtractionEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ParsedResumeRepository;
import ats.resume.infrastructure.persistence.repository.ResumeExtractionRepository;
import ats.resume.infrastructure.screening.ScoreRunWriter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Предварительный скор при загрузке: MASKED → SCORING → SCORED / SCORING_FAILED. Сетевой вызов
 * scoring-service идёт вне транзакции, чтобы не держать соединение с БД на время LLM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreliminaryScoringService {

    private static final String EXTRACTION_VERSION = "extract-v1";

    private final ApplicationRepository applicationRepository;
    private final ParsedResumeRepository parsedResumeRepository;
    private final ResumeExtractionRepository extractionRepository;
    private final VacancyClient vacancyClient;
    private final ScoringClient scoringClient;
    private final ScoreRunWriter scoreRunWriter;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    /** Запуск после загрузки. Ошибки не пробрасываются в HTTP-запрос: статус отклика их хранит. */
    public void scoreOnUpload(UUID applicationId) {
        ApplicationEntity application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            return;
        }
        long startedAt = System.nanoTime();
        log.info("preliminary_scoring_started application_id={}", applicationId);
        VacancyTemplate template;
        try {
            template = vacancyClient.getTemplate(application.getVacancyId());
        } catch (RuntimeException e) {
            log.warn(
                    "vacancy_template_fetch_failed application_id={} error={}",
                    applicationId,
                    e.getClass().getSimpleName());
            fail(applicationId);
            return;
        }
        try {
            score(applicationId, template);
            log.info(
                    "preliminary_scoring_finished application_id={} elapsed_ms={}",
                    applicationId,
                    (System.nanoTime() - startedAt) / 1_000_000);
        } catch (RuntimeException e) {
            log.warn(
                    "preliminary_scoring_failed application_id={} error={}",
                    applicationId,
                    e.getClass().getSimpleName());
        }
    }

    /**
     * Считает PRELIMINARY по переданному шаблону. Если отклик уже забрал другой worker, возвращает
     * null. Резюме разбирается один раз и сохраняется: повторный расчёт LLM не вызывает.
     */
    public ScoreRunEntity score(UUID applicationId, VacancyTemplate template) {
        ApplicationSnapshot snapshot;
        try {
            snapshot = transactionTemplate.execute(status -> start(applicationId));
        } catch (RuntimeException e) {
            fail(applicationId);
            throw e;
        }
        if (snapshot == null) {
            return null;
        }
        try {
            ParsedResume parsed = snapshot.parsed();
            if (parsed == null) {
                parsed = scoringClient.extract(snapshot.maskedText());
                ParsedResume extracted = parsed;
                transactionTemplate.executeWithoutResult(
                        status -> saveExtracted(applicationId, extracted));
            }
            ScoringResult result =
                    scoringClient.score(
                            new ScoringRequest(
                                    applicationId, snapshot.maskedText(), template, parsed));
            return transactionTemplate.execute(status -> complete(applicationId, template, result));
        } catch (RuntimeException e) {
            fail(applicationId);
            throw e;
        }
    }

    private ApplicationSnapshot start(UUID applicationId) {
        if (applicationRepository.markScoring(applicationId) == 0) {
            return null;
        }
        ApplicationEntity application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new IllegalStateException("application not found"));
        ParsedResumeEntity parsed =
                parsedResumeRepository
                        .findByResumeId(application.getResumeId())
                        .orElseThrow(() -> new IllegalStateException("parsed resume not found"));
        ParsedResume extracted =
                extractionRepository
                        .findFirstByResumeIdOrderByCreatedAtDesc(application.getResumeId())
                        .map(entity -> new ParsedResume(entity.getExtractedFeatures()))
                        .orElse(null);
        return new ApplicationSnapshot(parsed.getMaskedText(), extracted);
    }

    private void saveExtracted(UUID applicationId, ParsedResume parsed) {
        UUID resumeId =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new IllegalStateException("application not found"))
                        .getResumeId();
        ResumeExtractionEntity entity = new ResumeExtractionEntity();
        entity.setResumeId(resumeId);
        entity.setExtractedFeatures(parsed.base64());
        entity.setExtractionVersion(EXTRACTION_VERSION);
        extractionRepository.save(entity);
    }

    private ScoreRunEntity complete(
            UUID applicationId, VacancyTemplate template, ScoringResult result) {
        ScoreRunEntity run =
                scoreRunWriter.write(
                        applicationId,
                        null,
                        "PRELIMINARY",
                        template,
                        scoringClient.version(),
                        result);
        ApplicationEntity application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new IllegalStateException("application not found"));
        application.setPreliminaryScoreRunId(run.getId());
        application.setStatus("SCORED");
        applicationRepository.save(application);
        if ("HH".equals(application.getSource())) {
            eventPublisher.publishEvent(
                    new HhApplicationScoredEvent(
                            applicationId,
                            template.vacancyId(),
                            template.title(),
                            run.getScore().doubleValue(),
                            run.getScoreLow().doubleValue(),
                            run.getScoreHigh().doubleValue()));
        }
        return run;
    }

    /** Публичная точка для случая, когда задача не попала в очередь и worker не запустится. */
    public void markFailed(UUID applicationId) {
        fail(applicationId);
    }

    private void fail(UUID applicationId) {
        try {
            transactionTemplate.executeWithoutResult(
                    status -> applicationRepository.updateStatus(applicationId, "SCORING_FAILED"));
        } catch (RuntimeException e) {
            log.warn(
                    "application_status_update_failed application_id={} target_status=SCORING_FAILED"
                            + " error={}",
                    applicationId,
                    e.getClass().getSimpleName());
        }
    }

    private record ApplicationSnapshot(String maskedText, ParsedResume parsed) {}
}
