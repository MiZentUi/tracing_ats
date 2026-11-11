package ats.resume.infrastructure.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ats.resume.config.ScreeningProperties;
import ats.resume.domain.scoring.ExternalSkillEvidence;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.domain.vacancy.VacancyClient;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.resume.domain.vault.EncryptedPii;
import ats.resume.domain.vault.PiiPayload;
import ats.resume.domain.vault.PiiVaultCrypto;
import ats.resume.infrastructure.github.GitHubEnricher;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.PiiVaultEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ExternalEvidenceRepository;
import ats.resume.infrastructure.persistence.repository.PiiVaultRepository;
import ats.resume.infrastructure.persistence.repository.ScoreRunRepository;
import ats.resume.infrastructure.persistence.repository.ScreeningBatchRepository;
import ats.resume.infrastructure.scoring.PreliminaryScoringService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScreeningWorkerTest {

    private final ScreeningBatchRepository batchRepository = mock(ScreeningBatchRepository.class);
    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final PiiVaultRepository piiVaultRepository = mock(PiiVaultRepository.class);
    private final ExternalEvidenceRepository externalEvidenceRepository =
            mock(ExternalEvidenceRepository.class);
    private final ScoreRunRepository scoreRunRepository = mock(ScoreRunRepository.class);
    private final VacancyClient vacancyClient = mock(VacancyClient.class);
    private final PreliminaryScoringService preliminaryScoringService =
            mock(PreliminaryScoringService.class);
    private final ScoreRunWriter scoreRunWriter = mock(ScoreRunWriter.class);
    private final PiiVaultCrypto piiVaultCrypto = mock(PiiVaultCrypto.class);
    private final GitHubEnricher gitHubEnricher = mock(GitHubEnricher.class);
    private final JsonCodec jsonCodec = mock(JsonCodec.class);

    private ScreeningWorker worker;
    private ScreeningProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ScreeningProperties();
        properties.setScoringWait(Duration.ofMillis(50));
        properties.setScoringPoll(Duration.ofMillis(10));
        worker =
                new ScreeningWorker(
                        batchRepository,
                        applicationRepository,
                        piiVaultRepository,
                        externalEvidenceRepository,
                        scoreRunRepository,
                        vacancyClient,
                        preliminaryScoringService,
                        scoreRunWriter,
                        piiVaultCrypto,
                        gitHubEnricher,
                        jsonCodec,
                        properties);
        when(batchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jsonCodec.write(any())).thenReturn("{}");
    }

    @Test
    void marksApplicationFailedWhenScoringDoesNotFinishBeforeDeadline() {
        UUID batchId = UUID.randomUUID();
        UUID vacancyId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ScreeningBatchEntity batch = batch(batchId, vacancyId);
        ApplicationEntity application = application(applicationId, vacancyId, "SCORING", false);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(vacancyClient.getTemplate(vacancyId)).thenReturn(template(vacancyId, 2));

        worker.run(batchId, vacancyId, List.of(applicationId));

        assertThat(application.getStatus()).isEqualTo("FAILED");
        assertThat(batch.getFailed()).isEqualTo(1);
        assertThat(batch.getProcessed()).isZero();
        assertThat(batch.getStatus()).isEqualTo("FAILED");
        verify(vacancyClient, never()).decideScreening(any(), any());
    }

    @Test
    void countsScoringFailedAsBatchFailureWithoutSkipping() {
        UUID batchId = UUID.randomUUID();
        UUID vacancyId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ScreeningBatchEntity batch = batch(batchId, vacancyId);
        ApplicationEntity application =
                application(applicationId, vacancyId, "SCORING_FAILED", false);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(vacancyClient.getTemplate(vacancyId)).thenReturn(template(vacancyId, 2));

        worker.run(batchId, vacancyId, List.of(applicationId));

        assertThat(batch.getFailed()).isEqualTo(1);
        assertThat(batch.getProcessed()).isZero();
        assertThat(batch.getStatus()).isEqualTo("FAILED");
        verify(vacancyClient, never()).decideScreening(any(), any());
    }

    @Test
    void recalculatesStalePreliminaryAndCopiesItToFinal() {
        UUID batchId = UUID.randomUUID();
        UUID vacancyId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ScreeningBatchEntity batch = batch(batchId, vacancyId);
        ApplicationEntity application = application(applicationId, vacancyId, "SCORED", false);
        VacancyTemplate template = template(vacancyId, 2);
        ScoreRunEntity stale = run(applicationId, 1);
        ScoreRunEntity current = run(applicationId, 2);
        ScoreRunEntity finalRun = run(applicationId, 2);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(vacancyClient.getTemplate(vacancyId)).thenReturn(template);
        when(scoreRunRepository.findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(
                        applicationId, "PRELIMINARY"))
                .thenReturn(Optional.of(stale), Optional.of(current));
        when(preliminaryScoringService.score(applicationId, template)).thenReturn(current);
        when(vacancyClient.decideScreening(eq(vacancyId), any()))
                .thenReturn(
                        List.of(
                                new VacancyClient.ScreeningResult(
                                        applicationId,
                                        VacancyClient.ScreeningDecision.NOT_SHORTLISTED,
                                        false)));
        when(scoreRunWriter.copy(current, batchId, "FINAL")).thenReturn(finalRun);

        worker.run(batchId, vacancyId, List.of(applicationId));

        verify(preliminaryScoringService).score(applicationId, template);
        verify(scoreRunWriter).copy(current, batchId, "FINAL");
        assertThat(application.getFinalScoreRunId()).isEqualTo(finalRun.getId());
        assertThat(application.getStatus()).isEqualTo("REFINED");
        assertThat(batch.getProcessed()).isEqualTo(1);
        assertThat(batch.getFailed()).isZero();
        assertThat(batch.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void usesCurrentPreliminaryWithoutRecalculation() {
        UUID batchId = UUID.randomUUID();
        UUID vacancyId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ScreeningBatchEntity batch = batch(batchId, vacancyId);
        ApplicationEntity application = application(applicationId, vacancyId, "SCORED", false);
        VacancyTemplate template = template(vacancyId, 2);
        ScoreRunEntity current = run(applicationId, 2);
        ScoreRunEntity finalRun = run(applicationId, 2);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(vacancyClient.getTemplate(vacancyId)).thenReturn(template);
        when(scoreRunRepository.findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(
                        applicationId, "PRELIMINARY"))
                .thenReturn(Optional.of(current));
        when(vacancyClient.decideScreening(eq(vacancyId), any()))
                .thenReturn(
                        List.of(
                                new VacancyClient.ScreeningResult(
                                        applicationId,
                                        VacancyClient.ScreeningDecision.NOT_SHORTLISTED,
                                        false)));
        when(scoreRunWriter.copy(current, batchId, "FINAL")).thenReturn(finalRun);

        worker.run(batchId, vacancyId, List.of(applicationId));

        verify(preliminaryScoringService, never()).score(any(), any());
        verify(scoreRunWriter).copy(current, batchId, "FINAL");
        assertThat(application.getStatus()).isEqualTo("REFINED");
    }

    @Test
    void enrichesOnlyShortlistedApplicationsWithGithubConsent() {
        UUID batchId = UUID.randomUUID();
        UUID vacancyId = UUID.randomUUID();
        UUID shortlistedId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        ScreeningBatchEntity batch = batch(batchId, vacancyId);
        ApplicationEntity shortlisted = application(shortlistedId, vacancyId, "SCORED", true);
        ApplicationEntity rejected = application(rejectedId, vacancyId, "SCORED", true);
        VacancyTemplate template = template(vacancyId, 2);
        ScoreRunEntity shortlistedRun = run(shortlistedId, 2);
        ScoreRunEntity rejectedRun = run(rejectedId, 2);
        ScoreRunEntity shortlistedFinal = run(shortlistedId, 2);
        ScoreRunEntity rejectedFinal = run(rejectedId, 2);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(applicationRepository.findById(shortlistedId)).thenReturn(Optional.of(shortlisted));
        when(applicationRepository.findById(rejectedId)).thenReturn(Optional.of(rejected));
        when(vacancyClient.getTemplate(vacancyId)).thenReturn(template);
        when(scoreRunRepository.findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(
                        shortlistedId, "PRELIMINARY"))
                .thenReturn(Optional.of(shortlistedRun));
        when(scoreRunRepository.findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(
                        rejectedId, "PRELIMINARY"))
                .thenReturn(Optional.of(rejectedRun));
        when(vacancyClient.decideScreening(eq(vacancyId), any()))
                .thenReturn(
                        List.of(
                                new VacancyClient.ScreeningResult(
                                        shortlistedId,
                                        VacancyClient.ScreeningDecision.SHORTLISTED,
                                        true),
                                new VacancyClient.ScreeningResult(
                                        rejectedId,
                                        VacancyClient.ScreeningDecision.NOT_SHORTLISTED,
                                        false)));
        when(piiVaultRepository.findByResumeId(shortlisted.getResumeId()))
                .thenReturn(Optional.of(vault(shortlisted.getResumeId())));
        when(piiVaultCrypto.decrypt(eq(shortlisted.getResumeId()), any(EncryptedPii.class)))
                .thenReturn(
                        new PiiPayload(List.of(), null, "https://github.com/alice", null, null));
        when(gitHubEnricher.enrich(eq("https://github.com/alice"), any(), any(Instant.class)))
                .thenReturn(
                        List.of(
                                new ExternalSkillEvidence(
                                        "Java", 0.8, Map.of("repos", "1"), List.of())));
        when(scoreRunWriter.copy(shortlistedRun, batchId, "FINAL")).thenReturn(shortlistedFinal);
        when(scoreRunWriter.copy(rejectedRun, batchId, "FINAL")).thenReturn(rejectedFinal);

        worker.run(batchId, vacancyId, List.of(shortlistedId, rejectedId));

        verify(gitHubEnricher).enrich(eq("https://github.com/alice"), any(), any(Instant.class));
        verify(gitHubEnricher, never())
                .enrich(eq("https://github.com/bob"), any(), any(Instant.class));
        verify(externalEvidenceRepository).save(any());
        assertThat(shortlisted.getStatus()).isEqualTo("ENRICHED");
        assertThat(rejected.getStatus()).isEqualTo("REFINED");
    }

    private ScreeningBatchEntity batch(UUID batchId, UUID vacancyId) {
        ScreeningBatchEntity batch = new ScreeningBatchEntity();
        batch.setId(batchId);
        batch.setVacancyId(vacancyId);
        batch.setStatus("PENDING");
        batch.setTotal(1);
        return batch;
    }

    private ApplicationEntity application(
            UUID applicationId, UUID vacancyId, String status, boolean consentGithub) {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(applicationId);
        application.setVacancyId(vacancyId);
        application.setResumeId(UUID.randomUUID());
        application.setCandidateId(UUID.randomUUID());
        application.setStatus(status);
        application.setConsentGithub(consentGithub);
        return application;
    }

    private ScoreRunEntity run(UUID applicationId, int templateVersion) {
        ScoreRunEntity run = new ScoreRunEntity();
        run.setId(UUID.randomUUID());
        run.setApplicationId(applicationId);
        run.setPhase("PRELIMINARY");
        run.setTemplateVersion(templateVersion);
        run.setScore(BigDecimal.valueOf(72.5));
        run.setScoreLow(BigDecimal.valueOf(60));
        run.setScoreHigh(BigDecimal.valueOf(85));
        return run;
    }

    private PiiVaultEntity vault(UUID resumeId) {
        PiiVaultEntity vault = new PiiVaultEntity();
        vault.setResumeId(resumeId);
        vault.setKeyVersion("test");
        vault.setIv(new byte[] {1});
        vault.setCiphertext(new byte[] {2});
        return vault;
    }

    private VacancyTemplate template(UUID vacancyId, int version) {
        Requirement requirement =
                new Requirement(
                        UUID.randomUUID(), "Java", "Java", RequirementKind.MUST, 3, true, 3, 5);
        return new VacancyTemplate(
                vacancyId,
                version,
                "Java-разработчик",
                "java-developer",
                "middle",
                "Ищем Java-разработчика.",
                5,
                0.8,
                0.7,
                0.15,
                0.15,
                "prompt-v1",
                "scoring-v1",
                "weights-v1",
                List.of(requirement));
    }
}
