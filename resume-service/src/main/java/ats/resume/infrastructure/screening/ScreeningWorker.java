package ats.resume.infrastructure.screening;

import ats.resume.config.ScreeningProperties;
import ats.resume.domain.scoring.ExternalSkillEvidence;
import ats.resume.domain.vacancy.VacancyClient;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.resume.domain.vault.EncryptedPii;
import ats.resume.domain.vault.PiiPayload;
import ats.resume.domain.vault.PiiVaultCrypto;
import ats.resume.infrastructure.github.GitHubEnricher;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ExternalEvidenceEntity;
import ats.resume.infrastructure.persistence.entity.PiiVaultEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ExternalEvidenceRepository;
import ats.resume.infrastructure.persistence.repository.PiiVaultRepository;
import ats.resume.infrastructure.persistence.repository.ScoreRunRepository;
import ats.resume.infrastructure.persistence.repository.ScreeningBatchRepository;
import ats.resume.infrastructure.scoring.PreliminaryScoringService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Асинхронный скрининг по готовым PRELIMINARY-запускам: ждёт скоринг при загрузке, пересчитывает
 * устаревшие по версии шаблона, один раз вызывает DecideScreening и копирует запуск в FINAL. GitHub
 * зовётся только для шорт-листа с согласием и не уменьшает балл при отсутствии.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScreeningWorker {

    private final ScreeningBatchRepository batchRepository;
    private final ApplicationRepository applicationRepository;
    private final PiiVaultRepository piiVaultRepository;
    private final ExternalEvidenceRepository externalEvidenceRepository;
    private final ScoreRunRepository scoreRunRepository;
    private final VacancyClient vacancyClient;
    private final PreliminaryScoringService preliminaryScoringService;
    private final ScoreRunWriter scoreRunWriter;
    private final PiiVaultCrypto piiVaultCrypto;
    private final GitHubEnricher gitHubEnricher;
    private final JsonCodec jsonCodec;
    private final ScreeningProperties screeningProperties;

    public void run(UUID batchId, UUID vacancyId, List<UUID> applicationIds) {
        ScreeningBatchEntity batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return;
        }
        batch.setStatus("RUNNING");
        batch.setStartedAt(Instant.now());
        batchRepository.save(batch);

        int processed = 0;
        int failed = 0;
        try {
            VacancyTemplate template = vacancyClient.getTemplate(vacancyId);
            List<ScoredApplication> scored = new ArrayList<>();

            for (UUID applicationId : applicationIds) {
                try {
                    ScoredApplication item = prepare(applicationId, template);
                    if (item == null) {
                        failed++;
                    } else {
                        scored.add(item);
                        processed++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    markFailed(applicationId);
                    log.warn(
                            "Screening preparation failed: applicationId={}, error={}",
                            applicationId,
                            e.getClass().getSimpleName());
                }
                batch.setProcessed(processed);
                batch.setFailed(failed);
                batchRepository.save(batch);
            }

            if (!scored.isEmpty()) {
                applyScreeningDecisions(vacancyId, batchId, template, scored);
            }

            batch.setProcessed(processed);
            batch.setFailed(failed);
            batch.setStatus(
                    failed == applicationIds.size() && !applicationIds.isEmpty()
                            ? "FAILED"
                            : "COMPLETED");
            batch.setFinishedAt(Instant.now());
            batchRepository.save(batch);
        } catch (RuntimeException e) {
            batch.setStatus("FAILED");
            batch.setErrorMessage("screening failed");
            batch.setProcessed(processed);
            batch.setFailed(failed);
            batch.setFinishedAt(Instant.now());
            batchRepository.save(batch);
            log.error(
                    "Screening batch failed: batchId={}, error={}",
                    batchId,
                    e.getClass().getSimpleName());
        }
    }

    /** Возвращает null, если отклик не удалось подготовить; это считается failed, а не skip. */
    private ScoredApplication prepare(UUID applicationId, VacancyTemplate template) {
        ApplicationEntity application = awaitScoring(applicationId);
        if (application == null || !"SCORED".equals(application.getStatus())) {
            return null;
        }
        ScoreRunEntity run = currentPreliminary(applicationId, template);
        if (run == null) {
            return null;
        }
        return new ScoredApplication(
                applicationId,
                run.getScore().doubleValue(),
                run.getScoreLow().doubleValue(),
                run.getScoreHigh().doubleValue());
    }

    /**
     * Ждёт завершения скоринга при загрузке. Если дедлайн истёк, отклик получает FAILED: пакет
     * должен показать ошибку, а не молча пропустить кандидата.
     */
    private ApplicationEntity awaitScoring(UUID applicationId) {
        Instant deadline = Instant.now().plus(screeningProperties.getScoringWait());
        while (true) {
            ApplicationEntity application =
                    applicationRepository.findById(applicationId).orElse(null);
            if (application == null) {
                return null;
            }
            if (!"SCORING".equals(application.getStatus())) {
                return application;
            }
            if (!Instant.now().isBefore(deadline)) {
                application.setStatus("FAILED");
                applicationRepository.save(application);
                log.warn("Scoring wait timed out: applicationId={}", applicationId);
                return application;
            }
            sleep(screeningProperties.getScoringPoll());
        }
    }

    /** Устаревший или отсутствующий PRELIMINARY пересчитывается по текущей версии шаблона. */
    private ScoreRunEntity currentPreliminary(UUID applicationId, VacancyTemplate template) {
        ScoreRunEntity run = latestPreliminary(applicationId);
        if (isCurrent(run, template)) {
            return run;
        }
        ScoreRunEntity scored = preliminaryScoringService.score(applicationId, template);
        if (scored != null && isCurrent(scored, template)) {
            return scored;
        }
        ApplicationEntity application = awaitScoring(applicationId);
        if (application == null || !"SCORED".equals(application.getStatus())) {
            return null;
        }
        run = latestPreliminary(applicationId);
        return isCurrent(run, template) ? run : null;
    }

    private ScoreRunEntity latestPreliminary(UUID applicationId) {
        return scoreRunRepository
                .findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(applicationId, "PRELIMINARY")
                .orElse(null);
    }

    private boolean isCurrent(ScoreRunEntity run, VacancyTemplate template) {
        return run != null
                && run.getTemplateVersion() != null
                && run.getTemplateVersion() == template.version();
    }

    private void applyScreeningDecisions(
            UUID vacancyId,
            UUID batchId,
            VacancyTemplate template,
            List<ScoredApplication> scored) {
        List<VacancyClient.ScreeningCandidate> candidates =
                scored.stream()
                        .map(
                                item ->
                                        new VacancyClient.ScreeningCandidate(
                                                item.applicationId(),
                                                item.score(),
                                                item.scoreLow(),
                                                item.scoreHigh()))
                        .toList();
        Map<UUID, VacancyClient.ScreeningResult> decisions =
                vacancyClient.decideScreening(vacancyId, candidates).stream()
                        .collect(
                                Collectors.toMap(
                                        VacancyClient.ScreeningResult::applicationId,
                                        Function.identity(),
                                        (left, right) -> left));

        Instant now = Instant.now();
        for (ScoredApplication item : scored) {
            ApplicationEntity application =
                    applicationRepository.findById(item.applicationId()).orElse(null);
            if (application == null) {
                continue;
            }
            VacancyClient.ScreeningResult decision =
                    decisions.getOrDefault(
                            item.applicationId(),
                            new VacancyClient.ScreeningResult(
                                    item.applicationId(),
                                    VacancyClient.ScreeningDecision.NOT_SHORTLISTED,
                                    false));
            application.setScreeningDecision(decision.decision().name());
            applicationRepository.save(application);

            Map<String, ExternalSkillEvidence> external = Map.of();
            if (decision.decision() == VacancyClient.ScreeningDecision.SHORTLISTED
                    && application.isConsentGithub()) {
                external = enrichSafely(application, template, now);
            }

            ScoreRunEntity preliminary = latestPreliminary(item.applicationId());
            if (preliminary == null) {
                application.setStatus("FAILED");
                applicationRepository.save(application);
                continue;
            }
            // В текущем контракте нет Refine: FINAL переиспользует готовые факты PRELIMINARY.
            ScoreRunEntity finalRun = scoreRunWriter.copy(preliminary, batchId, "FINAL");
            persistExternalEvidence(application.getId(), finalRun.getId(), external);
            application.setFinalScoreRunId(finalRun.getId());
            application.setStatus(external.isEmpty() ? "REFINED" : "ENRICHED");
            applicationRepository.save(application);
        }
    }

    /** Любая ошибка внешнего источника превращается в пустой набор: балл не уменьшается. */
    private Map<String, ExternalSkillEvidence> enrichSafely(
            ApplicationEntity application, VacancyTemplate template, Instant now) {
        try {
            return enrich(application, template, now);
        } catch (RuntimeException e) {
            log.warn(
                    "GitHub enrichment failed: applicationId={}, error={}",
                    application.getId(),
                    e.getClass().getSimpleName());
            return Map.of();
        }
    }

    private Map<String, ExternalSkillEvidence> enrich(
            ApplicationEntity application, VacancyTemplate template, Instant now) {
        Optional<PiiVaultEntity> vault =
                piiVaultRepository.findByResumeId(application.getResumeId());
        if (vault.isEmpty()) {
            return Map.of();
        }
        PiiPayload payload =
                piiVaultCrypto.decrypt(
                        application.getResumeId(),
                        new EncryptedPii(
                                vault.get().getIv(),
                                vault.get().getCiphertext(),
                                vault.get().getKeyVersion()));
        if (payload.githubUrl() == null) {
            return Map.of();
        }
        List<ExternalSkillEvidence> evidence =
                gitHubEnricher.enrich(payload.githubUrl(), template.requirements(), now);
        Map<String, ExternalSkillEvidence> result = new LinkedHashMap<>();
        for (ExternalSkillEvidence item : evidence) {
            result.merge(
                    item.skill().toLowerCase(Locale.ROOT),
                    item,
                    (left, right) -> left.q() >= right.q() ? left : right);
        }
        return Map.copyOf(result);
    }

    private void persistExternalEvidence(
            UUID applicationId, UUID scoreRunId, Map<String, ExternalSkillEvidence> evidence) {
        for (ExternalSkillEvidence item : evidence.values()) {
            ExternalEvidenceEntity entity = new ExternalEvidenceEntity();
            entity.setApplicationId(applicationId);
            entity.setScoreRunId(scoreRunId);
            entity.setSource("GITHUB");
            entity.setSkill(item.skill());
            entity.setFacts(jsonCodec.write(item.facts()));
            entity.setFlags(jsonCodec.write(item.flags()));
            externalEvidenceRepository.save(entity);
        }
    }

    private void markFailed(UUID applicationId) {
        applicationRepository
                .findById(applicationId)
                .ifPresent(
                        application -> {
                            application.setStatus("FAILED");
                            applicationRepository.save(application);
                        });
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("screening interrupted", e);
        }
    }

    private record ScoredApplication(
            UUID applicationId, double score, double scoreLow, double scoreHigh) {}
}
