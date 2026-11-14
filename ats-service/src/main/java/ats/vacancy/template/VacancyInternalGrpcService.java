package ats.vacancy.template;

import ats.vacancy.proto.DecideScreeningRequest;
import ats.vacancy.proto.DecideScreeningResponse;
import ats.vacancy.proto.GetTemplateRequest;
import ats.vacancy.proto.RequirementKind;
import ats.vacancy.proto.ScoringWeights;
import ats.vacancy.proto.ScreeningDecision;
import ats.vacancy.proto.ScreeningResult;
import ats.vacancy.proto.VacancyInternalServiceGrpc;
import ats.vacancy.proto.VacancyTemplate;
import ats.vacancy.screening.ScreeningPolicy;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

/** gRPC-фасад vacancy-service для resume-service. */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class VacancyInternalGrpcService
        extends VacancyInternalServiceGrpc.VacancyInternalServiceImplBase {

    private final VacancyTemplateQuery templateQuery;
    private final ScreeningPolicy screeningPolicy;

    @Override
    public void getTemplate(
            GetTemplateRequest request, StreamObserver<VacancyTemplate> responseObserver) {
        UUID vacancyId;
        try {
            vacancyId = UUID.fromString(request.getVacancyId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("vacancy_id must be a UUID")
                            .asRuntimeException());
            return;
        }

        templateQuery
                .findCurrent(vacancyId)
                .ifPresentOrElse(
                        template -> {
                            responseObserver.onNext(toProto(template));
                            responseObserver.onCompleted();
                        },
                        () ->
                                responseObserver.onError(
                                        Status.NOT_FOUND
                                                .withDescription("vacancy template not found")
                                                .asRuntimeException()));
    }

    @Override
    public void decideScreening(
            DecideScreeningRequest request,
            StreamObserver<DecideScreeningResponse> responseObserver) {
        List<ScreeningPolicy.CandidateScore> candidates =
                request.getCandidatesList().stream()
                        .map(
                                candidate ->
                                        new ScreeningPolicy.CandidateScore(
                                                candidate.getApplicationId(),
                                                candidate.getScore(),
                                                candidate.getScoreLow(),
                                                candidate.getScoreHigh()))
                        .toList();

        DecideScreeningResponse.Builder response = DecideScreeningResponse.newBuilder();
        for (ScreeningPolicy.DecisionResult result : screeningPolicy.decide(candidates)) {
            response.addResults(
                    ScreeningResult.newBuilder()
                            .setApplicationId(result.applicationId())
                            .setDecision(toProto(result.decision()))
                            .setEnrich(result.enrich())
                            .build());
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private VacancyTemplate toProto(VacancyTemplateData template) {
        VacancyTemplate.Builder builder =
                VacancyTemplate.newBuilder()
                        .setVacancyId(template.vacancyId().toString())
                        .setTemplateVersion(template.version())
                        .setTitle(template.title())
                        .setSpecializationCode(template.specializationCode())
                        .setLevelCode(template.levelCode())
                        .setSourceText(nullToEmpty(template.sourceText()))
                        .setTenure(Math.max(0, template.tenure()))
                        .setGithubWeight(template.githubWeight())
                        .setWeights(
                                ScoringWeights.newBuilder()
                                        .setWM(template.wM())
                                        .setWN(template.wN())
                                        .setWF(template.wF())
                                        .build())
                        .setPromptVersion(nullToEmpty(template.promptVersion()))
                        .setScoringVersion(nullToEmpty(template.scoringVersion()))
                        .setWeightsVersion(nullToEmpty(template.weightsVersion()));

        for (VacancyTemplateData.RequirementData requirement : template.requirements()) {
            builder.addRequirements(
                    ats.vacancy.proto.Requirement.newBuilder()
                            .setRequirementId(requirement.id().toString())
                            .setText(requirement.text())
                            .setSkill(requirement.skill())
                            .setKind(toProto(requirement.kind()))
                            .setWeight(requirement.weight())
                            .setCritical(requirement.critical())
                            .setYearsRequired(requirement.yearsRequired())
                            .setHalfLifeYears(requirement.halfLifeYears())
                            .setQualification(requirement.qualification())
                            .build());
        }
        return builder.build();
    }

    private RequirementKind toProto(String kind) {
        return switch (kind) {
            case "MUST" -> RequirementKind.REQUIREMENT_KIND_MUST;
            case "NICE" -> RequirementKind.REQUIREMENT_KIND_NICE;
            default -> RequirementKind.REQUIREMENT_KIND_OPTIONAL;
        };
    }

    private ScreeningDecision toProto(ScreeningPolicy.Decision decision) {
        return switch (decision) {
            case SHORTLISTED -> ScreeningDecision.SCREENING_DECISION_SHORTLISTED;
            case BORDERLINE -> ScreeningDecision.SCREENING_DECISION_BORDERLINE;
            case NOT_SHORTLISTED -> ScreeningDecision.SCREENING_DECISION_NOT_SHORTLISTED;
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
