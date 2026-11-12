package ats.resume.infrastructure.scoring;

import ats.resume.config.ScoringGrpcProperties;
import ats.resume.domain.scoring.EvidenceValidator;
import ats.resume.domain.scoring.ScoringClient;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.scoring.proto.ScoringServiceGrpc;
import ats.vacancy.proto.FeatureAssessment;
import ats.vacancy.proto.FeatureRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import scoring_service.v1.models.Messages;
import scoring_service.v1.models.features.Common.FeatureCriticality;
import scoring_service.v1.models.features.Education.EducationPerResume;
import scoring_service.v1.models.features.Experience.PriorExperiencePerResume;
import scoring_service.v1.models.features.Skill.SkillPerResume;
import scoring_service.v1.models.features.Skill.SkillRequirement;

/**
 * gRPC-адаптер к scoring-service. Контракт внешний: Java передаёт обезличенный текст и шаблон,
 * получает баллы в шкале 0..1 и переводит их в 0..100 для API и БД. Арифметики скоринга здесь нет.
 */
@Component
@RequiredArgsConstructor
public class GrpcScoringClient implements ScoringClient {

    private static final String REFINE_FLAG = "REFINE_RECOMMENDED";
    private static final String NO_EVIDENCE_GAP = "Навык не найден в обезличенном тексте";
    private static final String WEAK_EVIDENCE_GAP = "Недостаточно подтверждений";

    private final ScoringServiceGrpc.ScoringServiceBlockingStub stub;
    private final ScoringGrpcProperties properties;

    @Override
    public ScoringResult score(ScoringRequest request) {
        Messages.ScoreResumeResponse response;
        try {
            response =
                    stub.withDeadlineAfter(
                                    properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                            .scoreResume(toProto(request));
        } catch (RuntimeException e) {
            throw new ScoringClientException(
                    "scoring-service ScoreResume failed: " + e.getClass().getSimpleName(), e);
        }
        return toDomain(response, request);
    }

    @Override
    public String version() {
        return "grpc-scoring-service-v1";
    }

    private Messages.ScoreResumeRequest toProto(ScoringRequest request) {
        VacancyTemplate template = request.template();
        Messages.Vacancy.Builder vacancy =
                Messages.Vacancy.newBuilder()
                        .setTenure(Math.max(0, template.tenure()))
                        .setTextDescription(nullToEmpty(template.sourceText()))
                        .setPosition(position(template.levelCode()))
                        .setCategory(nullToEmpty(template.specializationCode()));
        for (Requirement requirement : template.requirements()) {
            vacancy.putRequirements(requirement.id().toString(), requirement(requirement));
        }
        return Messages.ScoreResumeRequest.newBuilder()
                .setVacancy(vacancy)
                .setResume(
                        Messages.UnprocessedResume.newBuilder()
                                .setText(nullToEmpty(request.maskedText())))
                .build();
    }

    private FeatureRequirement requirement(Requirement requirement) {
        String skill =
                nullToEmpty(requirement.skill()).isBlank()
                        ? requirement.text()
                        : requirement.skill();
        FeatureRequirement.Builder builder =
                FeatureRequirement.newBuilder()
                        .setCriticality(criticality(requirement))
                        .setSkill(
                                SkillRequirement.newBuilder()
                                        .setName(nullToEmpty(skill))
                                        .setNormalizedName(normalize(skill))
                                        .setDescription(nullToEmpty(requirement.text()))
                                        .setTenure(
                                                Math.max(
                                                        0,
                                                        (int)
                                                                Math.round(
                                                                        requirement
                                                                                .yearsRequired())))
                                        .build());
        return builder.build();
    }

    private FeatureCriticality criticality(Requirement requirement) {
        if (requirement.critical()) {
            return FeatureCriticality.FEATURE_CRITICALITY_CRITICAL;
        }
        return switch (requirement.kind()) {
            case MUST -> FeatureCriticality.FEATURE_CRITICALITY_REQUIRED;
            case NICE -> FeatureCriticality.FEATURE_CRITICALITY_NICE;
            case OPTIONAL -> FeatureCriticality.FEATURE_CRITICALITY_OPTIONAL;
        };
    }

    private ScoringResult toDomain(Messages.ScoreResumeResponse response, ScoringRequest request) {
        double score = percent(response.getScore());
        double low = Math.min(percent(response.getScoreLow()), score);
        double high = Math.max(percent(response.getScoreHigh()), score);
        List<String> flags = new ArrayList<>();
        if (response.getRefine()) {
            flags.add(REFINE_FLAG);
        }
        List<ScoringResult.RequirementResult> requirements =
                request.template().requirements().stream()
                        .map(
                                requirement ->
                                        requirementResult(
                                                requirement,
                                                response.getFeaturesMap()
                                                        .get(requirement.id().toString()),
                                                request.maskedText()))
                        .toList();
        return new ScoringResult(
                clamp01(response.getRelevance()),
                score,
                low,
                high,
                response.hasTenure() ? response.getTenure() : null,
                flags,
                requirements);
    }

    private ScoringResult.RequirementResult requirementResult(
            Requirement requirement, FeatureAssessment assessment, String maskedText) {
        double relevance = assessment == null ? 0 : clamp01(assessment.getRelevance());
        double coverage = assessment == null ? 0 : clamp01(assessment.getCoverage());
        Integer tenure =
                assessment == null || !assessment.hasTenure() ? null : assessment.getTenure();
        Optional<String> quote =
                assessment == null
                        ? Optional.empty()
                        : EvidenceValidator.validate(quote(assessment), maskedText);
        if (quote.isEmpty() && coverage > 0) {
            // Балл без проверяемой цитаты не считается доказанным: покрытие не повышается.
            coverage = 0;
        }
        ScoringResult.RequirementResult.Status status =
                coverage >= 0.8
                        ? ScoringResult.RequirementResult.Status.COVERED
                        : coverage >= 0.4
                                ? ScoringResult.RequirementResult.Status.PARTIAL
                                : ScoringResult.RequirementResult.Status.GAP;
        String gap =
                status == ScoringResult.RequirementResult.Status.COVERED
                        ? null
                        : quote.isEmpty() ? NO_EVIDENCE_GAP : WEAK_EVIDENCE_GAP;
        return new ScoringResult.RequirementResult(
                requirement.id().toString(),
                requirement.text(),
                requirement.kind(),
                requirement.weight(),
                requirement.critical(),
                relevance,
                coverage,
                tenure,
                status,
                quote.isPresent() ? ScoringResult.RequirementResult.EvidenceSource.RESUME : null,
                quote.orElse(null),
                gap,
                false);
    }

    private String quote(FeatureAssessment assessment) {
        return switch (assessment.getResumeCase()) {
            case SKILL -> {
                SkillPerResume skill = assessment.getSkill();
                yield skill.hasEvidence() ? skill.getEvidence().getText() : null;
            }
            case EDUCATION -> {
                EducationPerResume education = assessment.getEducation();
                yield education.hasEvidence() ? education.getEvidence().getText() : null;
            }
            case EXPERIENCE -> {
                PriorExperiencePerResume experience = assessment.getExperience();
                yield experience.getMetricsList().stream()
                        .map(evidence -> evidence.getText())
                        .filter(text -> text != null && !text.isBlank())
                        .findFirst()
                        .orElse(null);
            }
            default -> null;
        };
    }

    private Messages.JobPositionLevel position(String levelCode) {
        return switch (normalize(levelCode)) {
            case "intern" -> Messages.JobPositionLevel.JOB_POSITION_INTERN;
            case "junior" -> Messages.JobPositionLevel.JOB_POSITION_JUNIOR;
            case "middle" -> Messages.JobPositionLevel.JOB_POSITION_MIDDLE;
            case "senior" -> Messages.JobPositionLevel.JOB_POSITION_SENIOR;
            default -> Messages.JobPositionLevel.JOB_POSITION_UNSPECIFIED;
        };
    }

    private String normalize(String value) {
        return nullToEmpty(value).strip().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double percent(double value) {
        return Math.max(0, Math.min(100, clamp01(value) * 100));
    }

    private double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
