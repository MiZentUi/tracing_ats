package ats.resume.infrastructure.vacancy;

import ats.resume.config.VacancyGrpcProperties;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.domain.vacancy.VacancyClient;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.vacancy.proto.DecideScreeningRequest;
import ats.vacancy.proto.DecideScreeningResponse;
import ats.vacancy.proto.GetTemplateRequest;
import ats.vacancy.proto.VacancyInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** gRPC-адаптер к vacancy-service. Здесь только транспорт и маппинг, без бизнес-логики. */
@Component
@RequiredArgsConstructor
public class GrpcVacancyClient implements VacancyClient {

    private final VacancyInternalServiceGrpc.VacancyInternalServiceBlockingStub stub;
    private final VacancyGrpcProperties properties;

    @Override
    public VacancyTemplate getTemplate(UUID vacancyId) {
        try {
            ats.vacancy.proto.VacancyTemplate response =
                    stub.withDeadlineAfter(
                                    properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                            .getTemplate(
                                    GetTemplateRequest.newBuilder()
                                            .setVacancyId(vacancyId.toString())
                                            .build());
            return toDomain(response);
        } catch (StatusRuntimeException e) {
            throw new VacancyClientException(
                    "vacancy-service GetTemplate failed: " + e.getStatus(), e);
        }
    }

    @Override
    public List<ScreeningResult> decideScreening(
            UUID vacancyId, List<ScreeningCandidate> candidates) {
        DecideScreeningRequest.Builder request =
                DecideScreeningRequest.newBuilder().setVacancyId(vacancyId.toString());
        for (ScreeningCandidate candidate : candidates) {
            request.addCandidates(
                    ats.vacancy.proto.ScreeningCandidate.newBuilder()
                            .setApplicationId(candidate.applicationId().toString())
                            .setScore(candidate.score())
                            .setScoreLow(candidate.scoreLow())
                            .setScoreHigh(candidate.scoreHigh())
                            .build());
        }
        try {
            DecideScreeningResponse response =
                    stub.withDeadlineAfter(
                                    properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                            .decideScreening(request.build());
            return response.getResultsList().stream()
                    .map(
                            result ->
                                    new ScreeningResult(
                                            UUID.fromString(result.getApplicationId()),
                                            toDomain(result.getDecision()),
                                            result.getEnrich()))
                    .toList();
        } catch (StatusRuntimeException e) {
            throw new VacancyClientException(
                    "vacancy-service DecideScreening failed: " + e.getStatus(), e);
        }
    }

    private VacancyTemplate toDomain(ats.vacancy.proto.VacancyTemplate template) {
        return new VacancyTemplate(
                UUID.fromString(template.getVacancyId()),
                template.getTemplateVersion(),
                template.getTitle(),
                template.getSpecializationCode(),
                template.getLevelCode(),
                template.getSourceText(),
                template.getTenure(),
                template.getGithubWeight(),
                template.getWeights().getWM(),
                template.getWeights().getWN(),
                template.getWeights().getWF(),
                template.getPromptVersion(),
                template.getScoringVersion(),
                template.getWeightsVersion(),
                template.getRequirementsList().stream()
                        .map(
                                requirement ->
                                        new Requirement(
                                                UUID.fromString(requirement.getRequirementId()),
                                                requirement.getText(),
                                                requirement.getSkill(),
                                                toDomain(requirement.getKind()),
                                                requirement.getWeight(),
                                                requirement.getCritical(),
                                                requirement.getYearsRequired(),
                                                requirement.getHalfLifeYears(),
                                                requirement.getQualification()))
                        .toList());
    }

    private RequirementKind toDomain(ats.vacancy.proto.RequirementKind kind) {
        return switch (kind) {
            case REQUIREMENT_KIND_MUST -> RequirementKind.MUST;
            case REQUIREMENT_KIND_NICE -> RequirementKind.NICE;
            default -> RequirementKind.OPTIONAL;
        };
    }

    private ScreeningDecision toDomain(ats.vacancy.proto.ScreeningDecision decision) {
        return switch (decision) {
            case SCREENING_DECISION_SHORTLISTED -> ScreeningDecision.SHORTLISTED;
            case SCREENING_DECISION_BORDERLINE -> ScreeningDecision.BORDERLINE;
            default -> ScreeningDecision.NOT_SHORTLISTED;
        };
    }
}
