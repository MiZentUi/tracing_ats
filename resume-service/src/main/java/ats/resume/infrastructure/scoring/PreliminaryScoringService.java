package ats.resume.infrastructure.scoring;

import ats.resume.domain.scoring.ScoringClient;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.VacancyClient;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ParsedResumeRepository;
import ats.resume.infrastructure.screening.ScoreRunWriter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final ApplicationRepository applicationRepository;
    private final ParsedResumeRepository parsedResumeRepository;
    private final VacancyClient vacancyClient;
    private final ScoringClient scoringClient;
    private final ScoreRunWriter scoreRunWriter;
    private final TransactionTemplate transactionTemplate;

    /** Запуск после загрузки. Ошибки не пробрасываются в HTTP-запрос: статус отклика их хранит. */
    public void scoreOnUpload(UUID applicationId) {
        ApplicationEntity application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            return;
        }
        VacancyTemplate template;
        try {
            template = vacancyClient.getTemplate(application.getVacancyId());
        } catch (RuntimeException e) {
            log.warn(
                    "Preliminary scoring template fetch failed: applicationId={}, error={}",
                    applicationId,
                    e.getClass().getSimpleName());
            fail(applicationId);
            return;
        }
        try {
            score(applicationId, template);
        } catch (RuntimeException e) {
            log.warn(
                    "Preliminary scoring failed: applicationId={}, error={}",
                    applicationId,
                    e.getClass().getSimpleName());
        }
    }

    /**
     * Считает PRELIMINARY по переданному шаблону. Если отклик уже забрал другой worker, возвращает
     * null. Используется и при загрузке, и при пересчёте устаревшего запуска в скрининге.
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
            ScoringResult result =
                    scoringClient.score(
                            new ScoringRequest(applicationId, snapshot.maskedText(), template));
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
        return new ApplicationSnapshot(parsed.getMaskedText());
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
                    "Failed to mark scoring failure: applicationId={}, error={}",
                    applicationId,
                    e.getClass().getSimpleName());
        }
    }

    private record ApplicationSnapshot(String maskedText) {}
}
