package ats.resume.infrastructure.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.config.ScoringGrpcProperties;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.scoring.proto.ScoringServiceGrpc;
import ats.vacancy.proto.FeatureAssessment;
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
import scoring_service.v1.models.features.Common.FeatureCriticality;
import scoring_service.v1.models.features.Common.FeatureEvidence;
import scoring_service.v1.models.features.Skill.SkillPerResume;

class GrpcScoringClientTest {

    private Server server;
    private ManagedChannel channel;
    private FakeScoringService fake;
    private GrpcScoringClient client;

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
        client = new GrpcScoringClient(ScoringServiceGrpc.newBlockingStub(channel), properties);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void mapsRequestAndValidatesEvidence() {
        Requirement java =
                new Requirement(
                        UUID.randomUUID(), "Java", "Java", RequirementKind.MUST, 3, true, 3, 5);
        Requirement spring =
                new Requirement(
                        UUID.randomUUID(),
                        "Spring Boot",
                        "Spring Boot",
                        RequirementKind.MUST,
                        3,
                        false,
                        2,
                        3);
        Requirement kafka =
                new Requirement(
                        UUID.randomUUID(), "Kafka", "Kafka", RequirementKind.NICE, 1, false, 1, 3);
        VacancyTemplate template =
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
        String maskedText = "Java developer with 5 years. Spring Boot.";
        fake.response =
                Messages.ScoreResumeResponse.newBuilder()
                        .setRelevance(0.8)
                        .setScore(0.72)
                        .setScoreLow(0.6)
                        .setScoreHigh(0.85)
                        .setTenure(5)
                        .setRefine(true)
                        .putFeatures(
                                java.id().toString(),
                                FeatureAssessment.newBuilder()
                                        .setRelevance(0.9)
                                        .setCoverage(0.9)
                                        .setTenure(5)
                                        .setSkill(
                                                SkillPerResume.newBuilder()
                                                        .setName("Java")
                                                        .setNormalizedName("java")
                                                        .setEvidence(
                                                                FeatureEvidence.newBuilder()
                                                                        .setText(
                                                                                "Java developer with 5 years.")))
                                        .build())
                        .putFeatures(
                                spring.id().toString(),
                                FeatureAssessment.newBuilder()
                                        .setRelevance(0.5)
                                        .setCoverage(0.5)
                                        .setSkill(
                                                SkillPerResume.newBuilder()
                                                        .setName("Spring Boot")
                                                        .setNormalizedName("spring boot")
                                                        .setEvidence(
                                                                FeatureEvidence.newBuilder()
                                                                        .setText(
                                                                                "Нет такой цитаты")))
                                        .build())
                        .build();

        ScoringResult result =
                client.score(new ScoringRequest(UUID.randomUUID(), maskedText, template));

        assertThat(fake.request.getVacancy().getTenure()).isEqualTo(5);
        assertThat(fake.request.getVacancy().getTextDescription()).isEqualTo(template.sourceText());
        assertThat(fake.request.getVacancy().getPosition())
                .isEqualTo(Messages.JobPositionLevel.JOB_POSITION_MIDDLE);
        assertThat(fake.request.getVacancy().getCategory()).isEqualTo("java-developer");
        assertThat(fake.request.getVacancy().getRequirementsMap()).hasSize(3);
        FeatureRequirement javaProto =
                fake.request.getVacancy().getRequirementsMap().get(java.id().toString());
        assertThat(javaProto.getCriticality())
                .isEqualTo(FeatureCriticality.FEATURE_CRITICALITY_CRITICAL);
        assertThat(javaProto.getSkill().getNormalizedName()).isEqualTo("java");
        assertThat(javaProto.getSkill().getTenure()).isEqualTo(3);

        assertThat(result.score()).isEqualTo(72);
        assertThat(result.scoreLow()).isEqualTo(60);
        assertThat(result.scoreHigh()).isEqualTo(85);
        assertThat(result.relevance()).isEqualTo(0.8);
        assertThat(result.tenure()).isEqualTo(5);
        assertThat(result.flags()).containsExactly("REFINE_RECOMMENDED");

        ScoringResult.RequirementResult javaResult = result.requirements().getFirst();
        assertThat(javaResult.status()).isEqualTo(ScoringResult.RequirementResult.Status.COVERED);
        assertThat(javaResult.coverage()).isEqualTo(0.9);
        assertThat(javaResult.tenure()).isEqualTo(5);
        assertThat(javaResult.evidenceQuote()).isEqualTo("Java developer with 5 years.");

        ScoringResult.RequirementResult springResult = result.requirements().get(1);
        assertThat(springResult.status()).isEqualTo(ScoringResult.RequirementResult.Status.GAP);
        assertThat(springResult.coverage()).isZero();
        assertThat(springResult.evidenceSource()).isNull();
        assertThat(springResult.evidenceQuote()).isNull();

        ScoringResult.RequirementResult kafkaResult = result.requirements().get(2);
        assertThat(kafkaResult.status()).isEqualTo(ScoringResult.RequirementResult.Status.GAP);
        assertThat(kafkaResult.relevance()).isZero();
        assertThat(kafkaResult.coverage()).isZero();
    }

    private static final class FakeScoringService
            extends ScoringServiceGrpc.ScoringServiceImplBase {

        private Messages.ScoreResumeRequest request;
        private Messages.ScoreResumeResponse response;

        @Override
        public void scoreResume(
                Messages.ScoreResumeRequest request,
                StreamObserver<Messages.ScoreResumeResponse> observer) {
            this.request = request;
            observer.onNext(response);
            observer.onCompleted();
        }
    }
}
