package ats.resume.infrastructure.screening;

import ats.resume.config.ResumeMatchProperties;
import ats.resume.domain.ingestion.ResumeMatchRequestedEvent;
import ats.resume.domain.scoring.ParsedResume;
import ats.resume.domain.scoring.ScoringBatchItem;
import ats.resume.domain.scoring.ScoringClient;
import ats.resume.domain.scoring.ScoringDirection;
import ats.resume.domain.vacancy.VacancyCatalog;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import ats.resume.infrastructure.persistence.entity.ResumeExtractionEntity;
import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.persistence.repository.ParsedResumeRepository;
import ats.resume.infrastructure.persistence.repository.ResumeExtractionRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import ats.resume.infrastructure.persistence.repository.ScreeningBatchRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Направление «вакансии под резюме»: одно резюме сравнивается со всеми вакансиями. Скоринг тот же,
 * что и для кандидатов под вакансию; вакансии уходят пакетами по `ats.resume.match.batch-size`.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeMatchService {

    private static final String EXTRACTION_VERSION = "extract-v1";

    private final ScreeningBatchRepository batchRepository;
    private final ResumeRepository resumeRepository;
    private final ParsedResumeRepository parsedResumeRepository;
    private final ResumeExtractionRepository extractionRepository;
    private final VacancyCatalog vacancyCatalog;
    private final ScoringClient scoringClient;
    private final ScoreRunWriter scoreRunWriter;
    private final ResumeMatchProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /** Ручной пересчёт: пакет создаётся синхронно, сравнение уходит в пул после коммита. */
    @Transactional
    public UUID start(UUID resumeId) {
        if (!resumeRepository.existsById(resumeId)) {
            throw new IllegalArgumentException("resume not found");
        }
        UUID batchId = createBatch(resumeId);
        eventPublisher.publishEvent(new ResumeMatchRequestedEvent(batchId, resumeId));
        return batchId;
    }

    /** Создаёт пакет синхронно, чтобы клиент сразу получил идентификатор прогресса. */
    public UUID createBatch(UUID resumeId) {
        ScreeningBatchEntity batch = new ScreeningBatchEntity();
        batch.setResumeId(resumeId);
        batch.setDirection(ScoringDirection.RESUME_TO_VACANCIES.name());
        batch.setStatus("PENDING");
        batch.setTotal(0);
        batch.setProcessed(0);
        batch.setFailed(0);
        return batchRepository.save(batch).getId();
    }

    public void run(UUID batchId, UUID resumeId) {
        ScreeningBatchEntity batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return;
        }
        batch.setStatus("RUNNING");
        batch.setStartedAt(Instant.now());
        batchRepository.save(batch);
        long startedAt = System.nanoTime();

        int processed = 0;
        int failed = 0;
        try {
            ResumeData data = load(resumeId);
            List<VacancyTemplate> templates = vacancyCatalog.listAll();
            batch.setTotal(templates.size());
            batchRepository.save(batch);
            log.info(
                    "resume_match_started batch_id={} resume_id={} vacancies={} batch_size={}",
                    batchId,
                    resumeId,
                    templates.size(),
                    properties.getBatchSize());

            ParsedResume parsed = data.parsed();
            if (parsed == null) {
                parsed = scoringClient.extract(data.maskedText());
                persistExtracted(resumeId, parsed);
            }

            for (List<VacancyTemplate> chunk : chunks(templates)) {
                List<ScoringBatchItem> items =
                        scoringClient.scoreBatch(parsed, data.maskedText(), chunk);
                Map<UUID, VacancyTemplate> byId = new LinkedHashMap<>();
                chunk.forEach(template -> byId.put(template.vacancyId(), template));

                for (ScoringBatchItem item : items) {
                    VacancyTemplate template = byId.get(item.vacancyId());
                    if (item.isFailed() || template == null) {
                        failed++;
                        log.warn(
                                "resume_match_vacancy_failed batch_id={} vacancy_id={} code={}",
                                batchId,
                                item.vacancyId(),
                                item.errorCode());
                        continue;
                    }
                    scoreRunWriter.writeMatch(
                            resumeId,
                            batchId,
                            "PRELIMINARY",
                            template,
                            scoringClient.version(),
                            item.result());
                    processed++;
                }
                batch.setProcessed(processed);
                batch.setFailed(failed);
                batchRepository.save(batch);
            }

            batch.setStatus("COMPLETED");
            batch.setProcessed(processed);
            batch.setFailed(failed);
            batch.setFinishedAt(Instant.now());
            batchRepository.save(batch);
            updateResumeStatus(resumeId, processed > 0 ? "MATCHED" : "MATCH_FAILED");
            log.info(
                    "resume_match_finished batch_id={} resume_id={} status=COMPLETED processed={}"
                            + " failed={} elapsed_ms={}",
                    batchId,
                    resumeId,
                    processed,
                    failed,
                    (System.nanoTime() - startedAt) / 1_000_000);
        } catch (RuntimeException e) {
            batch.setStatus("FAILED");
            batch.setErrorMessage("resume matching failed");
            batch.setProcessed(processed);
            batch.setFailed(failed);
            batch.setFinishedAt(Instant.now());
            batchRepository.save(batch);
            updateResumeStatus(resumeId, "MATCH_FAILED");
            log.error(
                    "resume_match_failed batch_id={} resume_id={} processed={} failed={}",
                    batchId,
                    resumeId,
                    processed,
                    failed,
                    e);
        }
    }

    /** Пакет не попал в очередь: резюме не должно остаться в промежуточном статусе. */
    public void markFailed(UUID batchId, UUID resumeId) {
        batchRepository
                .findById(batchId)
                .ifPresent(
                        batch -> {
                            batch.setStatus("FAILED");
                            batch.setErrorMessage("resume matching rejected");
                            batch.setFinishedAt(Instant.now());
                            batchRepository.save(batch);
                        });
        updateResumeStatus(resumeId, "MATCH_FAILED");
    }

    private List<List<VacancyTemplate>> chunks(List<VacancyTemplate> templates) {
        int size = Math.max(1, properties.getBatchSize());
        List<List<VacancyTemplate>> chunks = new ArrayList<>();
        for (int from = 0; from < templates.size(); from += size) {
            chunks.add(templates.subList(from, Math.min(from + size, templates.size())));
        }
        return chunks;
    }

    private ResumeData load(UUID resumeId) {
        ResumeEntity resume =
                resumeRepository
                        .findById(resumeId)
                        .orElseThrow(() -> new IllegalStateException("resume not found"));
        ParsedResumeEntity parsed =
                parsedResumeRepository
                        .findByResumeId(resumeId)
                        .orElseThrow(() -> new IllegalStateException("parsed resume not found"));
        ParsedResume features =
                extractionRepository
                        .findFirstByResumeIdOrderByCreatedAtDesc(resumeId)
                        .map(entity -> new ParsedResume(entity.getExtractedFeatures()))
                        .orElse(null);
        resume.setStatus("MATCHING");
        resumeRepository.save(resume);
        return new ResumeData(parsed.getMaskedText(), features);
    }

    private void persistExtracted(UUID resumeId, ParsedResume parsed) {
        ResumeExtractionEntity entity = new ResumeExtractionEntity();
        entity.setResumeId(resumeId);
        entity.setExtractedFeatures(parsed.base64());
        entity.setExtractionVersion(EXTRACTION_VERSION);
        extractionRepository.save(entity);
        updateResumeStatus(resumeId, "EXTRACTED");
    }

    private void updateResumeStatus(UUID resumeId, String status) {
        resumeRepository
                .findById(resumeId)
                .ifPresent(
                        resume -> {
                            resume.setStatus(status);
                            resumeRepository.save(resume);
                        });
    }

    private record ResumeData(String maskedText, ParsedResume parsed) {}
}
