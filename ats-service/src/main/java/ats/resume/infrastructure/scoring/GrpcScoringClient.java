package ats.resume.infrastructure.scoring;

import ats.resume.config.ScoringGrpcProperties;
import ats.resume.domain.scoring.EvidenceValidator;
import ats.resume.domain.scoring.ParsedResume;
import ats.resume.domain.scoring.QualificationCatalog;
import ats.resume.domain.scoring.ScoringBatchItem;
import ats.resume.domain.scoring.ScoringClient;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.scoring.proto.ScoringServiceGrpc;
import ats.vacancy.proto.FeatureRequirement;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.type.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import scoring_service.v1.models.Messages;
import scoring_service.v1.models.features.Common;
import scoring_service.v1.models.features.Qualification;
import scoring_service.v1.models.features.Skill;

/**
 * gRPC-адаптер к scoring-service. Разбирает резюме один раз (`ExtractResume`), затем считает
 * (`EvaluateResumeMatch` / `EvaluateResumeMatches`) без LLM. Арифметики скоринга здесь нет.
 */
@Component
@RequiredArgsConstructor
public class GrpcScoringClient implements ScoringClient {

    private static final String SCORING_VERSION = "grpc-scoring-service-v3";
    private static final String NO_EVIDENCE_GAP = "Навык не найден в обезличенном тексте";

    private final ScoringServiceGrpc.ScoringServiceBlockingStub stub;
    private final ScoringGrpcProperties properties;
    private final QualificationCatalog qualificationCatalog;

    @Override
    public ParsedResume extract(String maskedText) {
        Messages.ExtractResumeRequest request =
                Messages.ExtractResumeRequest.newBuilder()
                        .setResume(unprocessed(maskedText))
                        .setReferenceDate(referenceDate())
                        .build();
        try {
            Messages.ExtractResumeResponse response =
                    stub.withDeadlineAfter(
                                    properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                            .extractResume(request);
            return new ParsedResume(
                    Base64.getEncoder().encodeToString(response.getParsed().toByteArray()));
        } catch (RuntimeException e) {
            throw new ScoringClientException(
                    "scoring-service ExtractResume failed: " + e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public ScoringResult score(ScoringRequest request) {
        Messages.EvaluateResumeMatchRequest proto =
                Messages.EvaluateResumeMatchRequest.newBuilder()
                        .setResume(parsedResume(request.parsed()))
                        .setUnprocessedResume(unprocessed(request.maskedText()))
                        .setVacancy(vacancy(request.template()))
                        .setReferenceDate(referenceDate())
                        .setQualificationCatalog(qualificationCatalogSnapshot())
                        .build();
        try {
            Messages.EvaluateResumeMatchResponse response =
                    stub.withDeadlineAfter(
                                    properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                            .evaluateResumeMatch(proto);
            return toDomain(response, request.template(), request.maskedText());
        } catch (RuntimeException e) {
            throw new ScoringClientException(
                    "scoring-service EvaluateResumeMatch failed: " + e.getClass().getSimpleName(),
                    e);
        }
    }

    @Override
    public List<ScoringBatchItem> scoreBatch(
            ParsedResume parsed, String maskedText, List<VacancyTemplate> templates) {
        if (templates.isEmpty()) {
            return List.of();
        }
        Messages.EvaluateResumeMatchesRequest.Builder request =
                Messages.EvaluateResumeMatchesRequest.newBuilder()
                        .setResume(parsedResume(parsed))
                        .setUnprocessedResume(unprocessed(maskedText))
                        .setReferenceDate(referenceDate())
                        .setQualificationCatalog(qualificationCatalogSnapshot());
        for (VacancyTemplate template : templates) {
            request.putVacancies(template.vacancyId().toString(), vacancy(template));
        }
        Messages.EvaluateResumeMatchesResponse response;
        try {
            response =
                    stub.withDeadlineAfter(
                                    properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                            .evaluateResumeMatches(request.build());
        } catch (RuntimeException e) {
            throw new ScoringClientException(
                    "scoring-service EvaluateResumeMatches failed: " + e.getClass().getSimpleName(),
                    e);
        }

        List<ScoringBatchItem> items = new ArrayList<>(templates.size());
        for (VacancyTemplate template : templates) {
            UUID vacancyId = template.vacancyId();
            Messages.EvaluateResumeMatchResult result =
                    response.getResultsMap().get(vacancyId.toString());
            if (result == null) {
                items.add(
                        ScoringBatchItem.failed(
                                vacancyId, "INTERNAL", "vacancy is missing in scoring response"));
            } else if (result.hasError()) {
                items.add(
                        ScoringBatchItem.failed(
                                vacancyId,
                                result.getError().getCode(),
                                result.getError().getMessage()));
            } else {
                items.add(
                        ScoringBatchItem.ok(
                                vacancyId, toDomain(result.getMatch(), template, maskedText)));
            }
        }
        return items;
    }

    @Override
    public String version() {
        return SCORING_VERSION;
    }

    private ScoringResult toDomain(
            Messages.EvaluateResumeMatchResponse response,
            VacancyTemplate template,
            String maskedText) {
        Messages.GeneralResumeScore general = response.getGeneralScore();
        double score = percent(general.getScore());
        double low = Math.min(percent(general.getScoreLow()), score);
        double high = Math.max(percent(general.getScoreHigh()), score);
        List<ScoringResult.RequirementResult> requirements =
                template.requirements().stream()
                        .map(
                                requirement ->
                                        requirementResult(
                                                requirement,
                                                response.getMatchesMap()
                                                        .get(requirement.id().toString()),
                                                maskedText))
                        .toList();
        return new ScoringResult(
                clamp01(general.getRelevance()),
                score,
                low,
                high,
                general.hasTenure() ? general.getTenure() : null,
                List.of(),
                requirements,
                response.getStrengthsList(),
                response.getRisksList(),
                response.getQuestionsList(),
                response.getRecruiterNote());
    }

    private ScoringResult.RequirementResult requirementResult(
            Requirement requirement, Messages.FeatureMatch match, String maskedText) {
        if (match == null) {
            return new ScoringResult.RequirementResult(
                    requirement.id().toString(),
                    requirement.text(),
                    requirement.kind(),
                    requirement.weight(),
                    requirement.critical(),
                    0,
                    0,
                    null,
                    ScoringResult.RequirementResult.Status.NOT_STATED,
                    null,
                    null,
                    NO_EVIDENCE_GAP,
                    false);
        }

        Optional<String> quote =
                match.hasEvidence()
                        ? EvidenceValidator.validate(match.getEvidence().getText(), maskedText)
                        : Optional.empty();
        double coverage = clamp01(match.getCoverage());
        ScoringResult.RequirementResult.Status status = status(match.getStatus(), quote, coverage);
        if ((status == ScoringResult.RequirementResult.Status.COVERED
                        || status == ScoringResult.RequirementResult.Status.PARTIAL)
                && quote.isEmpty()) {
            // Балл без проверяемой цитаты не считается доказанным: покрытие не повышается.
            coverage = 0;
            status = ScoringResult.RequirementResult.Status.GAP;
        }
        Integer years = match.hasYearsFound() ? (int) Math.round(match.getYearsFound()) : null;
        String comment =
                quote.isEmpty() && status == ScoringResult.RequirementResult.Status.GAP
                        ? "Цитата не подтверждена обезличенным текстом"
                        : nullToEmpty(match.getComment());
        return new ScoringResult.RequirementResult(
                requirement.id().toString(),
                requirement.text(),
                requirement.kind(),
                requirement.weight(),
                requirement.critical(),
                clamp01(match.getRelevance()),
                coverage,
                years,
                status,
                quote.isPresent() ? ScoringResult.RequirementResult.EvidenceSource.RESUME : null,
                quote.orElse(null),
                comment.isBlank() ? NO_EVIDENCE_GAP : comment,
                false);
    }

    private ScoringResult.RequirementResult.Status status(
            Common.Correspondence correspondence, Optional<String> quote, double coverage) {
        return switch (correspondence) {
            case CORRESPONDENCE_FULL -> ScoringResult.RequirementResult.Status.COVERED;
            case CORRESPONDENCE_PARTIAL -> ScoringResult.RequirementResult.Status.PARTIAL;
            case CORRESPONDENCE_ABSENT -> ScoringResult.RequirementResult.Status.ABSENT;
            case CORRESPONDENCE_NOT_STATED -> ScoringResult.RequirementResult.Status.NOT_STATED;
            default ->
                    coverage > 0
                            ? ScoringResult.RequirementResult.Status.PARTIAL
                            : ScoringResult.RequirementResult.Status.NOT_STATED;
        };
    }

    private Messages.ParsedResume parsedResume(ParsedResume parsed) {
        try {
            return Messages.ParsedResume.parseFrom(Base64.getDecoder().decode(parsed.base64()));
        } catch (InvalidProtocolBufferException | IllegalArgumentException e) {
            throw new ScoringClientException("stored ParsedResume is invalid", e);
        }
    }

    private Messages.UnprocessedResume unprocessed(String maskedText) {
        return Messages.UnprocessedResume.newBuilder().setText(nullToEmpty(maskedText)).build();
    }

    private Messages.Vacancy vacancy(VacancyTemplate template) {
        Messages.Vacancy.Builder vacancy =
                Messages.Vacancy.newBuilder()
                        .setTenure(Math.max(0, template.tenure()))
                        .setTextDescription(nullToEmpty(template.sourceText()))
                        .setPosition(position(template.levelCode()))
                        .setCategory(nullToEmpty(template.specializationCode()));
        for (Requirement requirement : template.requirements()) {
            vacancy.putRequirements(requirement.id().toString(), requirement(requirement));
        }
        return vacancy.build();
    }

    private FeatureRequirement requirement(Requirement requirement) {
        String subject =
                nullToEmpty(requirement.skill()).isBlank()
                        ? requirement.text()
                        : requirement.skill();
        FeatureRequirement.Builder builder =
                FeatureRequirement.newBuilder().setCriticality(criticality(requirement));
        if (requirement.qualification()) {
            return builder.setQualification(
                            Qualification.QualificationRequirement.newBuilder()
                                    .setLabel(nullToEmpty(subject))
                                    .build())
                    .build();
        }
        return builder.setSkill(
                        Skill.SkillRequirement.newBuilder()
                                .setName(nullToEmpty(subject))
                                .setNormalizedName(normalize(subject))
                                .setDescription(nullToEmpty(requirement.text()))
                                .setTenure(
                                        Math.max(0, (int) Math.round(requirement.yearsRequired())))
                                .build())
                .build();
    }

    private Qualification.QualificationCatalog qualificationCatalogSnapshot() {
        Qualification.QualificationCatalog.Builder builder =
                Qualification.QualificationCatalog.newBuilder()
                        .setVersion(nullToEmpty(qualificationCatalog.version()));
        for (QualificationCatalog.Alias alias : qualificationCatalog.aliases()) {
            builder.addAliases(
                    Qualification.QualificationAlias.newBuilder()
                            .setAliasNormalized(nullToEmpty(alias.aliasNormalized()))
                            .setCanonicalKey(nullToEmpty(alias.canonicalKey()))
                            .setCanonicalLabel(nullToEmpty(alias.canonicalLabel()))
                            .build());
        }
        return builder.build();
    }

    private Common.FeatureCriticality criticality(Requirement requirement) {
        if (requirement.critical()) {
            return Common.FeatureCriticality.FEATURE_CRITICALITY_CRITICAL;
        }
        return switch (requirement.kind()) {
            case MUST -> Common.FeatureCriticality.FEATURE_CRITICALITY_REQUIRED;
            case NICE -> Common.FeatureCriticality.FEATURE_CRITICALITY_NICE;
            case OPTIONAL -> Common.FeatureCriticality.FEATURE_CRITICALITY_OPTIONAL;
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

    private Date referenceDate() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return Date.newBuilder()
                .setYear(today.getYear())
                .setMonth(today.getMonthValue())
                .setDay(today.getDayOfMonth())
                .build();
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
