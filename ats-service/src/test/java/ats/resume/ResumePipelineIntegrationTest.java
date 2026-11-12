package ats.resume;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.domain.ingestion.UploadOutcome;
import ats.resume.domain.scoring.ParsedResume;
import ats.resume.domain.scoring.ScoringBatchItem;
import ats.resume.domain.scoring.ScoringClient;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.domain.vacancy.VacancyCatalog;
import ats.resume.domain.vacancy.VacancyClient;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.resume.infrastructure.ingestion.ResumeIngestionService;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ParsedResumeRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import ats.resume.infrastructure.persistence.repository.ScoreRunRepository;
import ats.resume.infrastructure.persistence.repository.ScreeningBatchRepository;
import ats.resume.infrastructure.pii.ner.NerEntity;
import ats.resume.infrastructure.pii.ner.NerModel;
import ats.resume.infrastructure.ranking.RankingService;
import ats.resume.infrastructure.ranking.ResumeRankingService;
import ats.resume.infrastructure.report.ReportService;
import ats.resume.infrastructure.report.RevealService;
import ats.resume.infrastructure.screening.ResumeMatchService;
import ats.resume.infrastructure.screening.ScreeningWorker;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

/**
 * Сквозной сценарий MVP: загрузка → маскировка → Score_pre → шорт-лист → Score_final → рейтинг →
 * отчёт → reveal. GitHub и реальный vacancy-service заменены тестовыми адаптерами.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ResumePipelineIntegrationTest.FakeDependencies.class})
class ResumePipelineIntegrationTest {

    private static final UUID VACANCY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private ResumeIngestionService ingestionService;
    @Autowired private ScreeningWorker screeningWorker;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private ParsedResumeRepository parsedResumeRepository;
    @Autowired private ScoreRunRepository scoreRunRepository;
    @Autowired private ScreeningBatchRepository batchRepository;
    @Autowired private RankingService rankingService;
    @Autowired private ResumeRankingService resumeRankingService;
    @Autowired private ResumeMatchService resumeMatchService;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private ReportService reportService;
    @Autowired private RevealService revealService;

    @Test
    void uploadsScreensRanksReportsAndReveals() {
        String text =
                """
                Иван Петров
                Email: ivan.petrov@example.com
                Телефон: +7 999 123-45-67
                Высшее образование
                Московский государственный университет
                Специальность: Прикладная математика
                2018-2022
                Опыт работы
                Java developer with 5 years. Spring Boot, PostgreSQL.
                """;

        UploadOutcome outcome =
                ingestionService.ingest(
                        VACANCY_ID,
                        false,
                        new MockMultipartFile(
                                "files",
                                "resume.txt",
                                "text/plain",
                                text.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.MASKED);
        ParsedResumeEntity parsed =
                parsedResumeRepository.findByResumeId(outcome.resumeId()).orElseThrow();
        assertThat(parsed.getMaskedText())
                .doesNotContain("ivan.petrov@example.com")
                .doesNotContain("+7 999 123-45-67")
                .doesNotContain("Иван Петров")
                .contains("[PII_EMAIL_1]")
                .contains("[PII_NAME_1]")
                // Образование больше не маскируется: вуз остаётся в тексте.
                .contains("Московский государственный университет")
                .doesNotContain("[PII_UNIVERSITY_1]");
        assertThat(parsed.getEducationLevel()).isEqualTo("HIGHER");
        assertThat(parsed.getSpecialty()).isEqualTo("Прикладная математика");
        assertThat(parsed.getEducationYears()).isEqualByComparingTo("4.0");

        ApplicationEntity application =
                applicationRepository.findByVacancyId(VACANCY_ID).stream()
                        .filter(item -> item.getResumeId().equals(outcome.resumeId()))
                        .findFirst()
                        .orElseThrow();
        application = awaitStatus(application.getId(), "SCORED", Duration.ofSeconds(10));
        assertThat(application.getStatus()).isEqualTo("SCORED");
        assertThat(application.getPreliminaryScoreRunId()).isNotNull();

        ScreeningBatchEntity batch = new ScreeningBatchEntity();
        batch.setVacancyId(VACANCY_ID);
        batch.setStatus("PENDING");
        batch.setTotal(1);
        batch.setProcessed(0);
        batch.setFailed(0);
        batch = batchRepository.save(batch);
        screeningWorker.run(batch.getId(), VACANCY_ID, List.of(application.getId()));

        ScreeningBatchEntity completed = batchRepository.findById(batch.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getProcessed()).isEqualTo(1);
        assertThat(completed.getFailed()).isZero();

        ApplicationEntity screened =
                applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(screened.getScreeningDecision()).isEqualTo("SHORTLISTED");
        assertThat(screened.getPreliminaryScoreRunId()).isNotNull();
        assertThat(screened.getFinalScoreRunId()).isNotNull();
        assertThat(screened.getStatus()).isEqualTo("REFINED");

        ScoreRunEntity preliminary =
                scoreRunRepository
                        .findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(
                                application.getId(), "PRELIMINARY")
                        .orElseThrow();
        ScoreRunEntity finalRun =
                scoreRunRepository
                        .findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(
                                application.getId(), "FINAL")
                        .orElseThrow();
        assertThat(preliminary.getScore().doubleValue())
                .isBetween(
                        preliminary.getScoreLow().doubleValue(),
                        preliminary.getScoreHigh().doubleValue());
        assertThat(preliminary.getTemplateVersion()).isEqualTo(1);
        assertThat(preliminary.getRelevance()).isEqualByComparingTo("0.800");
        assertThat(finalRun.getScore().doubleValue())
                .isBetween(
                        finalRun.getScoreLow().doubleValue(),
                        finalRun.getScoreHigh().doubleValue());
        assertThat(finalRun.getTemplateVersion()).isEqualTo(1);

        RankingService.RankingView ranking = rankingService.ranking(VACANCY_ID, "FINAL");
        assertThat(ranking.items()).hasSize(1);
        assertThat(ranking.items().getFirst().applicationId()).isEqualTo(application.getId());

        ReportService.ReportView report =
                reportService.report(application.getId(), "FINAL").orElseThrow();
        assertThat(report.requirements()).hasSize(3);
        assertThat(report.relevance()).isEqualTo(0.8);
        ReportService.RequirementReportItem javaReport =
                report.requirements().stream()
                        .filter(item -> "Java".equals(item.text()))
                        .findFirst()
                        .orElseThrow();
        assertThat(javaReport.relevance()).isEqualTo(0.9);
        assertThat(javaReport.tenure()).isEqualTo(5);
        assertThat(report.blind()).isTrue();

        RevealService.RevealedCandidate revealed =
                revealService.reveal(application.getId(), "integration test").orElseThrow();
        assertThat(revealed.fullName()).isEqualTo("Иван Петров");
        assertThat(revealed.email()).isEqualTo("ivan.petrov@example.com");
        assertThat(revealed.university()).isNull();
    }

    @Test
    void uploadsResumeWithoutVacancyAndRanksVacancies() {
        String text =
                """
                Иван Петров
                Email: ivan.petrov@example.com
                Опыт работы
                Java developer with 5 years. Spring Boot, PostgreSQL.
                """;

        UploadOutcome outcome =
                ingestionService.ingest(
                        null,
                        false,
                        new MockMultipartFile(
                                "files",
                                "resume.txt",
                                "text/plain",
                                text.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.status()).isEqualTo(UploadOutcome.Status.MASKED);
        assertThat(outcome.batchId()).isNotNull();

        awaitResumeStatus(outcome.resumeId(), "MATCHED", Duration.ofSeconds(10));
        ScreeningBatchEntity batch = batchRepository.findById(outcome.batchId()).orElseThrow();
        assertThat(batch.getStatus()).isEqualTo("COMPLETED");
        assertThat(batch.getDirection()).isEqualTo("RESUME_TO_VACANCIES");
        assertThat(batch.getResumeId()).isEqualTo(outcome.resumeId());
        assertThat(batch.getTotal()).isEqualTo(4);
        assertThat(batch.getProcessed()).isEqualTo(4);
        assertThat(batch.getFailed()).isZero();

        ResumeRankingService.RankingView ranking =
                resumeRankingService.ranking(outcome.resumeId(), "PRELIMINARY");
        assertThat(ranking.items()).hasSize(4);
        assertThat(ranking.items().getFirst().rank()).isEqualTo(1);
        assertThat(ranking.items())
                .isSortedAccordingTo(
                        Comparator.comparingDouble(ResumeRankingService.RankingItem::score)
                                .reversed());

        UUID topVacancy = ranking.items().getFirst().vacancyId();
        ReportService.ReportView report =
                reportService
                        .reportForMatch(outcome.resumeId(), topVacancy, "PRELIMINARY")
                        .orElseThrow();
        assertThat(report.resumeId()).isEqualTo(outcome.resumeId());
        assertThat(report.vacancyId()).isEqualTo(topVacancy);
        assertThat(report.requirements()).hasSize(3);
        assertThat(report.applicationId()).isNull();

        // Повторный расчёт без LLM: пакет создаётся и считается по сохранённому ParsedResume.
        UUID secondBatch = resumeMatchService.createBatch(outcome.resumeId());
        resumeMatchService.run(secondBatch, outcome.resumeId());
        assertThat(batchRepository.findById(secondBatch).orElseThrow().getStatus())
                .isEqualTo("COMPLETED");
    }

    private void awaitResumeStatus(UUID resumeId, String status, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (status.equals(resumeRepository.findById(resumeId).orElseThrow().getStatus())) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private ApplicationEntity awaitStatus(UUID applicationId, String status, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            ApplicationEntity application =
                    applicationRepository.findById(applicationId).orElseThrow();
            if (status.equals(application.getStatus())) {
                return application;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return applicationRepository.findById(applicationId).orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeDependencies {

        @Bean
        @Primary
        VacancyClient vacancyClient() {
            VacancyTemplate template =
                    template(VACANCY_ID, "Java-разработчик (тест)", requirements());
            return new VacancyClient() {
                @Override
                public VacancyTemplate getTemplate(UUID vacancyId) {
                    return template;
                }

                @Override
                public List<ScreeningResult> decideScreening(
                        UUID vacancyId, List<ScreeningCandidate> candidates) {
                    return candidates.stream()
                            .map(
                                    candidate ->
                                            new ScreeningResult(
                                                    candidate.applicationId(),
                                                    ScreeningDecision.SHORTLISTED,
                                                    false))
                            .toList();
                }
            };
        }

        @Bean
        @Primary
        VacancyCatalog vacancyCatalog() {
            return () ->
                    List.of(
                            template(UUID.randomUUID(), "Java-разработчик", requirements()),
                            template(UUID.randomUUID(), "Backend Python", requirements()),
                            template(UUID.randomUUID(), "Аналитик данных", requirements()),
                            template(UUID.randomUUID(), "QA-инженер", requirements()));
        }

        private static List<Requirement> requirements() {
            return List.of(
                    new Requirement(
                            UUID.randomUUID(), "Java", "Java", RequirementKind.MUST, 3, true, 3, 5),
                    new Requirement(
                            UUID.randomUUID(),
                            "Spring Boot",
                            "Spring Boot",
                            RequirementKind.MUST,
                            3,
                            false,
                            2,
                            3),
                    new Requirement(
                            UUID.randomUUID(),
                            "Kafka",
                            "Kafka",
                            RequirementKind.NICE,
                            1,
                            false,
                            1,
                            3));
        }

        private static VacancyTemplate template(
                UUID vacancyId, String title, List<Requirement> requirements) {
            return new VacancyTemplate(
                    vacancyId,
                    1,
                    title,
                    "java-developer",
                    "middle",
                    "Ищем Java-разработчика. Java, Spring Boot.",
                    5,
                    0.8,
                    0.7,
                    0.15,
                    0.15,
                    "test-prompt-v1",
                    "test-scoring-v1",
                    "v1",
                    requirements);
        }

        @Bean
        @Primary
        ScoringClient scoringClient() {
            return new ScoringClient() {
                @Override
                public ParsedResume extract(String maskedText) {
                    return new ParsedResume(
                            java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
                }

                @Override
                public ScoringResult score(ScoringRequest request) {
                    return result(request.template());
                }

                @Override
                public List<ScoringBatchItem> scoreBatch(
                        ParsedResume parsed, String maskedText, List<VacancyTemplate> templates) {
                    return templates.stream()
                            .map(
                                    template ->
                                            ScoringBatchItem.ok(
                                                    template.vacancyId(), result(template)))
                            .toList();
                }

                private ScoringResult result(VacancyTemplate template) {
                    List<ScoringResult.RequirementResult> requirements = new ArrayList<>();
                    for (Requirement requirement : template.requirements()) {
                        boolean java = "Java".equalsIgnoreCase(requirement.skill());
                        boolean spring = "Spring Boot".equalsIgnoreCase(requirement.skill());
                        double coverage = java ? 0.9 : spring ? 0.8 : 0;
                        String quote =
                                java
                                        ? "Java developer with 5 years."
                                        : spring ? "Spring Boot" : null;
                        requirements.add(
                                new ScoringResult.RequirementResult(
                                        requirement.id().toString(),
                                        requirement.text(),
                                        requirement.kind(),
                                        requirement.weight(),
                                        requirement.critical(),
                                        java ? 0.9 : spring ? 0.8 : 0,
                                        coverage,
                                        java || spring ? 5 : null,
                                        coverage >= 0.8
                                                ? ScoringResult.RequirementResult.Status.COVERED
                                                : coverage >= 0.4
                                                        ? ScoringResult.RequirementResult.Status
                                                                .PARTIAL
                                                        : ScoringResult.RequirementResult.Status
                                                                .GAP,
                                        quote == null
                                                ? null
                                                : ScoringResult.RequirementResult.EvidenceSource
                                                        .RESUME,
                                        quote,
                                        quote == null ? "Навык не найден" : null,
                                        false));
                    }
                    return new ScoringResult(0.8, 72.5, 60, 85, 5, List.of(), requirements);
                }

                @Override
                public String version() {
                    return "test-scoring";
                }
            };
        }

        @Bean
        @Primary
        NerModel testNerModel() {
            return new NerModel() {
                @Override
                public List<NerEntity> detect(String text) {
                    int name = text.indexOf("Иван Петров");
                    int university = text.indexOf("Московский государственный университет");
                    return List.of(
                            new NerEntity(
                                    NerEntity.PERSON, name, name + "Иван Петров".length(), 0.99),
                            new NerEntity(
                                    NerEntity.ORGANIZATION,
                                    university,
                                    university + "Московский государственный университет".length(),
                                    0.99));
                }

                @Override
                public String modelName() {
                    return "test-ner";
                }

                @Override
                public String modelHash() {
                    return "test";
                }
            };
        }
    }
}
