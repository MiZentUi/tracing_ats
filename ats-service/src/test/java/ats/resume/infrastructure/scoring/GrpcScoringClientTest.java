package ats.resume.infrastructure.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.config.ScoringGrpcProperties;
import ats.resume.domain.scoring.ParsedResume;
import ats.resume.domain.scoring.ScoringBatchItem;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.scoring.proto.ScoringServiceGrpc;
import ats.vacancy.proto.FeatureRequirement;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scoring_service.v1.models.Messages;
import scoring_service.v1.models.features.Common;
import scoring_service.v1.models.features.Education;

class GrpcScoringClientTest {

    private Server server;
    private ManagedChannel channel;
    private FakeScoringService fake;
    private GrpcScoringClient client;
    private VacancyTemplate template;
    private Requirement java;
    private Requirement spring;
    private Requirement kafka;

    @BeforeEach
    void start() throws IOException {
        String name = "scoring-" + UUID.randomUUID();
        fake = new FakeScoringService();
        server =
                InProcessServerBuilder.forName(name)
                        .directExecutor()
                        .addService(fake)
                        .build()
                        .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        ScoringGrpcProperties properties = new ScoringGrpcProperties();
        properties.setDeadline(Duration.ofSeconds(2));
        client =
                new GrpcScoringClient(
                        ScoringServiceGrpc.newBlockingStub(channel),
                        properties,
                        new StubQualificationCatalog());

        java =
                new Requirement(
                        UUID.randomUUID(),
                        "Java",
                        "Java",
                        RequirementKind.MUST,
                        3,
                        true,
                        3,
                        5,
                        false);
        spring =
                new Requirement(
                        UUID.randomUUID(),
                        "Spring Boot",
                        "Spring Boot",
                        RequirementKind.MUST,
                        3,
                        false,
                        2,
                        3,
                        false);
        kafka =
                new Requirement(
                        UUID.randomUUID(),
                        "Kafka",
                        "Kafka",
                        RequirementKind.NICE,
                        1,
                        false,
                        1,
                        3,
                        false);
        template =
                new VacancyTemplate(
                        UUID.randomUUID(),
                        7,
                        "Java-разработчик",
                        "java-developer",
                        "middle",
                        "Ищем Java-разработчика. Java, Spring Boot, Kafka.",
                        5,
                        0.8,
                        0.7,
                        0.15,
                        0.15,
                        "prompt-v1",
                        "scoring-v1",
                        "weights-v1",
                        List.of(java, spring, kafka));
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void mapsEvaluationAndValidatesEvidence() {
        String maskedText = "Java developer with 5 years. Spring Boot.";
        fake.match =
                Messages.EvaluateResumeMatchResponse.newBuilder()
                        .setGeneralScore(
                                Messages.GeneralResumeScore.newBuilder()
                                        .setRelevance(0.8)
                                        .setScore(0.72)
                                        .setScoreLow(0.6)
                                        .setScoreHigh(0.85)
                                        .setTenure(5))
                        .putMatches(
                                java.id().toString(),
                                Messages.FeatureMatch.newBuilder()
                                        .setStatus(Common.Correspondence.CORRESPONDENCE_FULL)
                                        .setRelevance(0.9)
                                        .setCoverage(0.9)
                                        .setYearsFound(5)
                                        .setComment("Навык подтверждён")
                                        .setEvidence(evidence("Java developer with 5 years."))
                                        .build())
                        .putMatches(
                                spring.id().toString(),
                                Messages.FeatureMatch.newBuilder()
                                        .setStatus(Common.Correspondence.CORRESPONDENCE_PARTIAL)
                                        .setRelevance(0.5)
                                        .setCoverage(0.5)
                                        .setComment("Частично")
                                        .setEvidence(evidence("Нет такой цитаты"))
                                        .build())
                        .build();

        ScoringResult result =
                client.score(new ScoringRequest(UUID.randomUUID(), maskedText, template, parsed()));

        assertThat(fake.evaluateRequest.getVacancy().getTenure()).isEqualTo(5);
        assertThat(fake.evaluateRequest.getVacancy().getTextDescription())
                .isEqualTo(template.sourceText());
        assertThat(fake.evaluateRequest.getVacancy().getPosition())
                .isEqualTo(Messages.JobPositionLevel.JOB_POSITION_MIDDLE);
        assertThat(fake.evaluateRequest.getVacancy().getCategory()).isEqualTo("java-developer");
        assertThat(fake.evaluateRequest.getVacancy().getRequirementsMap()).hasSize(3);
        FeatureRequirement javaProto =
                fake.evaluateRequest.getVacancy().getRequirementsMap().get(java.id().toString());
        assertThat(javaProto.getCriticality())
                .isEqualTo(Common.FeatureCriticality.FEATURE_CRITICALITY_CRITICAL);
        assertThat(javaProto.getSkill().getNormalizedName()).isEqualTo("java");
        assertThat(javaProto.getSkill().getTenure()).isEqualTo(3);

        assertThat(result.score()).isEqualTo(72);
        assertThat(result.scoreLow()).isEqualTo(60);
        assertThat(result.scoreHigh()).isEqualTo(85);
        assertThat(result.relevance()).isEqualTo(0.8);
        assertThat(result.tenure()).isEqualTo(5);
        assertThat(result.flags()).isEmpty();

        ScoringResult.RequirementResult javaResult = result.requirements().getFirst();
        assertThat(javaResult.status()).isEqualTo(ScoringResult.RequirementResult.Status.COVERED);
        assertThat(javaResult.coverage()).isEqualTo(0.9);
        assertThat(javaResult.tenure()).isEqualTo(5);
        assertThat(javaResult.evidenceQuote()).isEqualTo("Java developer with 5 years.");
        assertThat(javaResult.gap()).isEqualTo("Навык подтверждён");

        // Цитаты нет в тексте: покрытие обнуляется, статус становится GAP.
        ScoringResult.RequirementResult springResult = result.requirements().get(1);
        assertThat(springResult.status()).isEqualTo(ScoringResult.RequirementResult.Status.GAP);
        assertThat(springResult.coverage()).isZero();
        assertThat(springResult.evidenceSource()).isNull();
        assertThat(springResult.evidenceQuote()).isNull();

        ScoringResult.RequirementResult kafkaResult = result.requirements().get(2);
        assertThat(kafkaResult.status())
                .isEqualTo(ScoringResult.RequirementResult.Status.NOT_STATED);
        assertThat(kafkaResult.relevance()).isZero();
        assertThat(kafkaResult.coverage()).isZero();
    }

    @Test
    void mapsQualificationAndSendsCatalogSnapshot() {
        Requirement qualification =
                new Requirement(
                        UUID.randomUUID(),
                        "3 группа по электробезопасности",
                        "3 группа по электробезопасности",
                        RequirementKind.MUST,
                        3,
                        true,
                        0,
                        0,
                        true);
        VacancyTemplate withQualification = withVacancy(template, UUID.randomUUID(), qualification);
        fake.match = matchResponse();

        client.score(
                new ScoringRequest(
                        UUID.randomUUID(),
                        "Допуск: 3 группа по электробезопасности.",
                        withQualification,
                        parsed()));

        FeatureRequirement requirement =
                fake.evaluateRequest
                        .getVacancy()
                        .getRequirementsMap()
                        .get(qualification.id().toString());
        assertThat(requirement.hasQualification()).isTrue();
        assertThat(requirement.getQualification().getLabel())
                .isEqualTo("3 группа по электробезопасности");
        assertThat(requirement.getSkill().getName()).isEmpty();
        assertThat(fake.evaluateRequest.getQualificationCatalog().getVersion())
                .isEqualTo("stub-v1");
        assertThat(fake.evaluateRequest.getQualificationCatalog().getAliasesCount())
                .isGreaterThan(0);
    }

    @Test
    void mapsEducationRequirement() {
        Requirement education =
                new Requirement(
                        UUID.randomUUID(),
                        "Высшее образование",
                        "Прикладная математика",
                        RequirementKind.MUST,
                        3,
                        false,
                        0,
                        0,
                        false,
                        "MASTER");
        VacancyTemplate withEducation = withVacancy(template, UUID.randomUUID(), education);
        fake.match = matchResponse();

        client.score(
                new ScoringRequest(
                        UUID.randomUUID(),
                        "МГУ, прикладная математика, магистратура.",
                        withEducation,
                        parsed()));

        FeatureRequirement requirement =
                fake.evaluateRequest
                        .getVacancy()
                        .getRequirementsMap()
                        .get(education.id().toString());
        assertThat(requirement.hasEducation()).isTrue();
        assertThat(requirement.getEducation().getMaster()).isEqualTo("Прикладная математика");
        assertThat(requirement.getEducation().getLevel())
                .isEqualTo(Education.EducationLevel.EDUCATION_LEVEL_MASTER);
        assertThat(requirement.hasSkill()).isFalse();
        assertThat(requirement.hasQualification()).isFalse();
    }

    @Test
    void batchKeepsPerVacancyErrors() {
        VacancyTemplate second = withVacancy(template, UUID.randomUUID(), spring);
        fake.batch =
                Messages.EvaluateResumeMatchesResponse.newBuilder()
                        .putResults(
                                template.vacancyId().toString(),
                                Messages.EvaluateResumeMatchResult.newBuilder()
                                        .setMatch(matchResponse())
                                        .build())
                        .putResults(
                                second.vacancyId().toString(),
                                Messages.EvaluateResumeMatchResult.newBuilder()
                                        .setError(
                                                Messages.BatchItemError.newBuilder()
                                                        .setCode("INVALID_ARGUMENT")
                                                        .setMessage("bad vacancy"))
                                        .build())
                        .build();

        List<ScoringBatchItem> items =
                client.scoreBatch(parsed(), "Java developer", List.of(template, second));

        assertThat(items).hasSize(2);
        assertThat(items.get(0).isFailed()).isFalse();
        assertThat(items.get(0).result().score()).isEqualTo(72);
        assertThat(items.get(1).isFailed()).isTrue();
        assertThat(items.get(1).errorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(fake.batchRequest.getQualificationCatalog().getVersion()).isEqualTo("stub-v1");
        assertThat(fake.batchRequest.getQualificationCatalog().getAliasesCount()).isGreaterThan(0);
    }

    private static Messages.EvaluateResumeMatchResponse matchResponse() {
        return Messages.EvaluateResumeMatchResponse.newBuilder()
                .setGeneralScore(
                        Messages.GeneralResumeScore.newBuilder()
                                .setRelevance(0.8)
                                .setScore(0.72)
                                .setScoreLow(0.6)
                                .setScoreHigh(0.85))
                .build();
    }

    private ParsedResume parsed() {
        return client.extract("Java developer");
    }

    private static Common.FeatureEvidence evidence(String text) {
        return Common.FeatureEvidence.newBuilder().setText(text).build();
    }

    private static VacancyTemplate withVacancy(VacancyTemplate source, UUID id, Requirement req) {
        return new VacancyTemplate(
                id,
                source.version(),
                source.title(),
                source.specializationCode(),
                source.levelCode(),
                source.sourceText(),
                source.tenure(),
                source.githubWeight(),
                source.wM(),
                source.wN(),
                source.wF(),
                source.promptVersion(),
                source.scoringVersion(),
                source.weightsVersion(),
                List.of(req));
    }

    private static final class FakeScoringService
            extends ScoringServiceGrpc.ScoringServiceImplBase {

        private Messages.EvaluateResumeMatchRequest evaluateRequest;
        private Messages.EvaluateResumeMatchesRequest batchRequest;
        private Messages.EvaluateResumeMatchResponse match =
                Messages.EvaluateResumeMatchResponse.getDefaultInstance();
        private Messages.EvaluateResumeMatchesResponse batch =
                Messages.EvaluateResumeMatchesResponse.getDefaultInstance();

        @Override
        public void extractResume(
                Messages.ExtractResumeRequest request,
                StreamObserver<Messages.ExtractResumeResponse> observer) {
            observer.onNext(
                    Messages.ExtractResumeResponse.newBuilder()
                            .setParsed(Messages.ParsedResume.newBuilder().setSchemaVersion(1))
                            .build());
            observer.onCompleted();
        }

        @Override
        public void evaluateResumeMatch(
                Messages.EvaluateResumeMatchRequest request,
                StreamObserver<Messages.EvaluateResumeMatchResponse> observer) {
            this.evaluateRequest = request;
            observer.onNext(match);
            observer.onCompleted();
        }

        @Override
        public void evaluateResumeMatches(
                Messages.EvaluateResumeMatchesRequest request,
                StreamObserver<Messages.EvaluateResumeMatchesResponse> observer) {
            this.batchRequest = request;
            observer.onNext(batch);
            observer.onCompleted();
        }
    }
}
