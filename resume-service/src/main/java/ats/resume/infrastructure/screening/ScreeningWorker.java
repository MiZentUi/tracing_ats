package ats.resume.infrastructure.screening;

import ats.resume.domain.privacy.EducationFacts;
import ats.resume.domain.scoring.ScoringClient;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.VacancyClient;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.resume.domain.vault.PiiPayload;
import ats.resume.domain.vault.PiiVaultCrypto;
import ats.resume.infrastructure.github.GitHubEnricher;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ExternalEvidenceEntity;
import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import ats.resume.infrastructure.persistence.entity.PiiVaultEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ExternalEvidenceRepository;
import ats.resume.infrastructure.persistence.repository.ParsedResumeRepository;
import ats.resume.infrastructure.persistence.repository.PiiVaultRepository;
import ats.resume.infrastructure.persistence.repository.ScreeningBatchRepository;
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
 * Асинхронный скрининг: Score_pre по тексту, один вызов DecideScreening на весь пакет, затем
 * Score_final. GitHub зовётся только для шорт-листа с согласием. Его отсутствие не штрафует.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScreeningWorker {

    private final ScreeningBatchRepository batchRepository;
    private final ApplicationRepository applicationRepository;
    private final ParsedResumeRepository parsedResumeRepository;
    private final PiiVaultRepository piiVaultRepository;
    private final ExternalEvidenceRepository externalEvidenceRepository;
    private final VacancyClient vacancyClient;
    private final ScoringClient scoringClient;
    private final ScoreRunWriter scoreRunWriter;
    private final PiiVaultCrypto piiVaultCrypto;
    private final GitHubEnricher gitHubEnricher;
    private final JsonCodec jsonCodec;

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
            Instant now = Instant.now();
            List<ScoredApplication> scored = new ArrayList<>();

            for (UUID applicationId : applicationIds) {
                try {
                    ScoredApplication item =
                            scorePreliminary(applicationId, batchId, template, now);
                    scored.add(item);
                    processed++;
                } catch (RuntimeException e) {
                    failed++;
                    markFailed(applicationId);
                    log.warn(
                            "Preliminary scoring failed: applicationId={}, error={}",
                            applicationId,
                            e.getClass().getSimpleName());
                }
                batch.setProcessed(processed);
                batch.setFailed(failed);
                batchRepository.save(batch);
            }

            if (!scored.isEmpty()) {
                applyScreeningDecisions(vacancyId, batchId, template, scored, now);
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

    private ScoredApplication scorePreliminary(
            UUID applicationId, UUID batchId, VacancyTemplate template, Instant now) {
        ApplicationEntity application =
                applicationRepository
                        .findById(applicationId)
                        .orElseThrow(() -> new IllegalStateException("application not found"));
        ParsedResumeEntity parsed =
                parsedResumeRepository
                        .findByResumeId(application.getResumeId())
                        .orElseThrow(() -> new IllegalStateException("parsed resume not found"));

        ScoringResult result =
                scoringClient.score(
                        new ScoringRequest(
                                applicationId,
                                parsed.getMaskedText(),
                                education(parsed),
                                template,
                                Map.of(),
                                now));
        ScoreRunEntity run =
                scoreRunWriter.write(
                        applicationId,
                        batchId,
                        "PRELIMINARY",
                        template,
                        scoringClient.version(),
                        result);

        application.setPreliminaryScoreRunId(run.getId());
        application.setStatus("SCREENED");
        applicationRepository.save(application);

        return new ScoredApplication(
                applicationId, result.score(), result.scoreLow(), result.scoreHigh());
    }

    private void applyScreeningDecisions(
            UUID vacancyId,
            UUID batchId,
            VacancyTemplate template,
            List<ScoredApplication> scored,
            Instant now) {
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

            Map<String, ScoringRequest.ExternalSkillEvidence> external = Map.of();
            if (decision.decision() == VacancyClient.ScreeningDecision.SHORTLISTED
                    && application.isConsentGithub()) {
                external = enrichSafely(application, template, now);
            }

            ScoringRequest request =
                    new ScoringRequest(
                            item.applicationId(),
                            parsedText(application),
                            parsedEducation(application),
                            template,
                            external,
                            now);
            ScoringResult finalResult = scoringClient.score(request);
            ScoreRunEntity finalRun =
                    scoreRunWriter.write(
                            item.applicationId(),
                            batchId,
                            "FINAL",
                            template,
                            scoringClient.version(),
                            finalResult);

            persistExternalEvidence(application.getId(), finalRun.getId(), external);
            application.setFinalScoreRunId(finalRun.getId());
            application.setStatus(external.isEmpty() ? "REFINED" : "ENRICHED");
            applicationRepository.save(application);
        }
    }

    private String parsedText(ApplicationEntity application) {
        return parsedResumeRepository
                .findByResumeId(application.getResumeId())
                .map(ParsedResumeEntity::getMaskedText)
                .orElse("");
    }

    private EducationFacts parsedEducation(ApplicationEntity application) {
        return parsedResumeRepository
                .findByResumeId(application.getResumeId())
                .map(this::education)
                .orElse(EducationFacts.empty());
    }

    private EducationFacts education(ParsedResumeEntity parsed) {
        return new EducationFacts(
                parsed.getEducationLevel(),
                parsed.getDegree(),
                parsed.getSpecialty(),
                parsed.getEducationYears() == null
                        ? null
                        : parsed.getEducationYears().doubleValue());
    }

    /** Любая ошибка внешнего источника превращается в пустой набор: балл не уменьшается. */
    private Map<String, ScoringRequest.ExternalSkillEvidence> enrichSafely(
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

    private Map<String, ScoringRequest.ExternalSkillEvidence> enrich(
            ApplicationEntity application, VacancyTemplate template, Instant now) {
        Optional<PiiVaultEntity> vault =
                piiVaultRepository.findByResumeId(application.getResumeId());
        if (vault.isEmpty()) {
            return Map.of();
        }
        PiiPayload payload =
                piiVaultCrypto.decrypt(
                        application.getResumeId(),
                        new ats.resume.domain.vault.EncryptedPii(
                                vault.get().getIv(),
                                vault.get().getCiphertext(),
                                vault.get().getKeyVersion()));
        if (payload.githubUrl() == null) {
            return Map.of();
        }
        List<ScoringRequest.ExternalSkillEvidence> evidence =
                gitHubEnricher.enrich(payload.githubUrl(), template.requirements(), now);
        Map<String, ScoringRequest.ExternalSkillEvidence> result = new LinkedHashMap<>();
        for (ScoringRequest.ExternalSkillEvidence item : evidence) {
            result.merge(
                    item.skill().toLowerCase(Locale.ROOT),
                    item,
                    (left, right) -> left.q() >= right.q() ? left : right);
        }
        return Map.copyOf(result);
    }

    private void persistExternalEvidence(
            UUID applicationId,
            UUID scoreRunId,
            Map<String, ScoringRequest.ExternalSkillEvidence> evidence) {
        for (ScoringRequest.ExternalSkillEvidence item : evidence.values()) {
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

    private record ScoredApplication(
            UUID applicationId, double score, double scoreLow, double scoreHigh) {}
}
