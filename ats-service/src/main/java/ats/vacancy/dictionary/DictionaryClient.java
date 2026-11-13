package ats.vacancy.dictionary;

import ats.dictionary.proto.AddQualificationAliasRequest;
import ats.dictionary.proto.Alias;
import ats.dictionary.proto.AliasInput;
import ats.dictionary.proto.ApplySkillInput;
import ats.dictionary.proto.ApplySkillsRequest;
import ats.dictionary.proto.ApplySkillsResponse;
import ats.dictionary.proto.CreateQualificationRequest;
import ats.dictionary.proto.CreateRelationRequest;
import ats.dictionary.proto.CreateSkillRequest;
import ats.dictionary.proto.DeleteAliasRequest;
import ats.dictionary.proto.DeleteQualificationAliasRequest;
import ats.dictionary.proto.DeleteQualificationRequest;
import ats.dictionary.proto.DeleteRelationRequest;
import ats.dictionary.proto.DeleteSkillRequest;
import ats.dictionary.proto.DeprecateSkillRequest;
import ats.dictionary.proto.DictionaryServiceGrpc;
import ats.dictionary.proto.GetVersionRequest;
import ats.dictionary.proto.ListQualificationsRequest;
import ats.dictionary.proto.ListRelationsRequest;
import ats.dictionary.proto.ListReviewItemsRequest;
import ats.dictionary.proto.ListSkillsRequest;
import ats.dictionary.proto.RawSkill;
import ats.dictionary.proto.RelationKind;
import ats.dictionary.proto.ResolveBatchRequest;
import ats.dictionary.proto.ResolveBatchResponse;
import ats.dictionary.proto.ResolveReviewItemRequest;
import ats.dictionary.proto.ResolveReviewItemResponse;
import ats.dictionary.proto.ResolveSource;
import ats.dictionary.proto.ReviewAction;
import ats.dictionary.proto.ReviewStatus;
import ats.dictionary.proto.SetCanonicalAliasRequest;
import ats.dictionary.proto.SetCanonicalQualificationAliasRequest;
import ats.dictionary.proto.SkillType;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Клиент словаря. Ошибки транспорта превращаются в {@link DictionaryUnavailableException}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictionaryClient {

    private final DictionaryServiceGrpc.DictionaryServiceBlockingStub stub;
    private final DictionaryProperties properties;

    public boolean enabled() {
        return properties.isEnabled();
    }

    public DictionaryModels.Resolved resolve(String raw, String source) {
        return resolve(raw, source, false);
    }

    public DictionaryModels.Resolved resolve(String raw, String source, boolean skipReview) {
        ResolveBatchResponse response =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .resolve(
                                                ResolveBatchRequest.newBuilder()
                                                        .addItems(
                                                                RawSkill.newBuilder()
                                                                        .setKey("0")
                                                                        .setRawName(
                                                                                raw == null
                                                                                        ? ""
                                                                                        : raw)
                                                                        .setSource(source(source)))
                                                        .setSkipReview(skipReview)
                                                        .build()));
        if (response.getItemsCount() == 0) {
            throw new DictionaryUnavailableException("dictionary returned no items");
        }
        var item = response.getItems(0);
        return new DictionaryModels.Resolved(
                item.getRawName(),
                item.getNormalizedKey(),
                item.getFoldedKey(),
                item.getSkillId().isBlank() ? null : UUID.fromString(item.getSkillId()),
                item.getCanonicalName(),
                item.getMethod().name().replace("RESOLVE_METHOD_", ""),
                item.getConfidence(),
                item.getCandidatesList().stream()
                        .map(
                                candidate ->
                                        new DictionaryModels.Candidate(
                                                UUID.fromString(candidate.getSkillId()),
                                                candidate.getCanonicalName(),
                                                candidate.getConfidence()))
                        .toList());
    }

    public String resolverVersion() {
        return call(() ->
                        stub.withDeadlineAfter(
                                        properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                                .getVersion(GetVersionRequest.getDefaultInstance()))
                .getResolverVersion();
    }

    public DictionaryModels.Skill createSkill(String canonicalName, String skillType) {
        var skill =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .createSkill(
                                                CreateSkillRequest.newBuilder()
                                                        .setCanonicalName(canonicalName)
                                                        .setSkillType(skillType(skillType))
                                                        .setActor("hr")
                                                        .build()));
        log.info("dictionary_skill_created skill_id={}", skill.getId());
        return toSkill(skill);
    }

    public DictionaryModels.Applied applySkills(List<DictionaryModels.ApplySkill> skills) {
        ApplySkillsRequest.Builder request = ApplySkillsRequest.newBuilder().setActor("hr");
        for (DictionaryModels.ApplySkill skill : skills) {
            ApplySkillInput.Builder input =
                    ApplySkillInput.newBuilder()
                            .setCanonical(skill.canonical())
                            .setSkillType(skillType(skill.skillType()));
            if (skill.targetSkillId() != null && !skill.targetSkillId().isBlank()) {
                input.setTargetSkillId(skill.targetSkillId());
            }
            for (DictionaryModels.ApplyAlias alias : skill.aliases()) {
                input.addAliases(
                        AliasInput.newBuilder()
                                .setText(alias.text())
                                .setLang(alias.lang() == null ? "mixed" : alias.lang()));
            }
            request.addSkills(input);
        }
        ApplySkillsResponse response =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .applySkills(request.build()));
        log.info(
                "dictionary_skills_applied skills={} resolver_version={}",
                response.getSkillsCount(),
                response.getResolverVersion());
        return new DictionaryModels.Applied(
                response.getSkillsList().stream().map(DictionaryClient::toSkill).toList(),
                response.getResolverVersion());
    }

    public DictionaryModels.Alias addAlias(
            UUID skillId, String raw, String lang, String lemmaMode) {
        Alias alias =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .addAlias(
                                                ats.dictionary.proto.AddAliasRequest.newBuilder()
                                                        .setSkillId(skillId.toString())
                                                        .setAliasRaw(raw)
                                                        .setLang(lang == null ? "mixed" : lang)
                                                        .setLemmaMode(
                                                                "RAW_ONLY".equals(lemmaMode)
                                                                        ? ats.dictionary.proto
                                                                                .LemmaMode
                                                                                .LEMMA_MODE_RAW_ONLY
                                                                        : ats.dictionary.proto
                                                                                .LemmaMode
                                                                                .LEMMA_MODE_AUTO)
                                                        .setActor("hr")
                                                        .build()));
        log.info("dictionary_alias_added skill_id={} alias_id={}", skillId, alias.getId());
        return toAlias(alias);
    }

    public List<DictionaryModels.Review> listReview(String status, int limit, int offset) {
        var response =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .listReviewItems(
                                                ListReviewItemsRequest.newBuilder()
                                                        .setStatus(reviewStatus(status))
                                                        .setLimit(limit)
                                                        .setOffset(offset)
                                                        .build()));
        List<DictionaryModels.Review> result = new ArrayList<>(response.getItemsCount());
        for (var item : response.getItemsList()) {
            result.add(toReview(item));
        }
        return result;
    }

    public DictionaryModels.ReviewResult resolveReview(
            UUID reviewId,
            String action,
            UUID skillId,
            String canonicalName,
            String skillType,
            List<String> additionalAliases) {
        ResolveReviewItemRequest.Builder request =
                ResolveReviewItemRequest.newBuilder()
                        .setReviewId(reviewId.toString())
                        .setAction(reviewAction(action))
                        .setActor("hr");
        if (skillId != null) {
            request.setSkillId(skillId.toString());
        }
        if (canonicalName != null) {
            request.setCanonicalName(canonicalName);
        }
        if (skillType != null) {
            request.setSkillType(skillType(skillType));
        }
        request.addAllAdditionalAliases(additionalAliases == null ? List.of() : additionalAliases);
        ResolveReviewItemResponse response =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .resolveReviewItem(request.build()));
        log.info(
                "dictionary_review_resolved review_id={} action={} skill_id={}",
                reviewId,
                action,
                response.getSkill().getId());
        return new DictionaryModels.ReviewResult(
                toReview(response.getItem()),
                response.hasSkill() ? toSkill(response.getSkill()) : null);
    }

    public List<DictionaryModels.Skill> listSkills(
            String query, String skillType, String status, int limit, int offset) {
        var response =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .listSkills(
                                                ListSkillsRequest.newBuilder()
                                                        .setQuery(query == null ? "" : query)
                                                        .setSkillType(skillType(skillType))
                                                        .setStatus(
                                                                "DEPRECATED".equals(status)
                                                                        ? ats.dictionary.proto
                                                                                .SkillStatus
                                                                                .SKILL_STATUS_DEPRECATED
                                                                        : ats.dictionary.proto
                                                                                .SkillStatus
                                                                                .SKILL_STATUS_ACTIVE)
                                                        .setLimit(limit)
                                                        .setOffset(offset)
                                                        .build()));
        return response.getItemsList().stream().map(DictionaryClient::toSkill).toList();
    }

    public DictionaryModels.Skill setCanonical(UUID skillId, UUID aliasId) {
        var skill =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .setCanonicalAlias(
                                                SetCanonicalAliasRequest.newBuilder()
                                                        .setSkillId(skillId.toString())
                                                        .setAliasId(aliasId.toString())
                                                        .setActor("hr")
                                                        .build()));
        log.info("dictionary_canonical_changed skill_id={} alias_id={}", skillId, aliasId);
        return toSkill(skill);
    }

    public void deleteAlias(UUID aliasId) {
        call(
                () ->
                        stub.withDeadlineAfter(
                                        properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                                .deleteAlias(
                                        DeleteAliasRequest.newBuilder()
                                                .setAliasId(aliasId.toString())
                                                .setActor("hr")
                                                .build()));
        log.info("dictionary_alias_deleted alias_id={}", aliasId);
    }

    public DictionaryModels.Skill deprecateSkill(UUID skillId) {
        var skill =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .deprecateSkill(
                                                DeprecateSkillRequest.newBuilder()
                                                        .setSkillId(skillId.toString())
                                                        .setActor("hr")
                                                        .build()));
        log.info("dictionary_skill_deprecated skill_id={}", skillId);
        return toSkill(skill);
    }

    public void deleteSkill(UUID skillId) {
        call(
                () ->
                        stub.withDeadlineAfter(
                                        properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                                .deleteSkill(
                                        DeleteSkillRequest.newBuilder()
                                                .setSkillId(skillId.toString())
                                                .setActor("hr")
                                                .build()));
        log.info("dictionary_skill_deleted skill_id={}", skillId);
    }

    public List<DictionaryModels.Qualification> listQualifications(
            String query, String status, int limit, int offset) {
        var response =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .listQualifications(
                                                ListQualificationsRequest.newBuilder()
                                                        .setQuery(query == null ? "" : query)
                                                        .setStatus(status == null ? "" : status)
                                                        .setLimit(limit)
                                                        .setOffset(offset)
                                                        .build()));
        return response.getItemsList().stream().map(DictionaryClient::toQualification).toList();
    }

    public DictionaryModels.Qualification createQualification(
            String canonicalName, String qualificationType) {
        var item =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .createQualification(
                                                CreateQualificationRequest.newBuilder()
                                                        .setCanonicalName(canonicalName)
                                                        .setQualificationType(
                                                                qualificationType == null
                                                                        ? "OTHER"
                                                                        : qualificationType)
                                                        .setActor("hr")
                                                        .build()));
        log.info("dictionary_qualification_created qualification_id={}", item.getId());
        return toQualification(item);
    }

    public DictionaryModels.QualificationAlias addQualificationAlias(
            UUID qualificationId, String raw, String lang, String lemmaMode) {
        var alias =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .addQualificationAlias(
                                                AddQualificationAliasRequest.newBuilder()
                                                        .setQualificationId(
                                                                qualificationId.toString())
                                                        .setAliasRaw(raw)
                                                        .setLang(lang == null ? "mixed" : lang)
                                                        .setLemmaMode(
                                                                "RAW_ONLY".equals(lemmaMode)
                                                                        ? ats.dictionary.proto
                                                                                .LemmaMode
                                                                                .LEMMA_MODE_RAW_ONLY
                                                                        : ats.dictionary.proto
                                                                                .LemmaMode
                                                                                .LEMMA_MODE_AUTO)
                                                        .setActor("hr")
                                                        .build()));
        return toQualificationAlias(alias);
    }

    public DictionaryModels.Qualification setCanonicalQualificationAlias(
            UUID qualificationId, UUID aliasId) {
        var item =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .setCanonicalQualificationAlias(
                                                SetCanonicalQualificationAliasRequest.newBuilder()
                                                        .setQualificationId(
                                                                qualificationId.toString())
                                                        .setAliasId(aliasId.toString())
                                                        .setActor("hr")
                                                        .build()));
        return toQualification(item);
    }

    public void deleteQualificationAlias(UUID aliasId) {
        call(
                () ->
                        stub.withDeadlineAfter(
                                        properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                                .deleteQualificationAlias(
                                        DeleteQualificationAliasRequest.newBuilder()
                                                .setAliasId(aliasId.toString())
                                                .setActor("hr")
                                                .build()));
    }

    public void deleteQualification(UUID qualificationId) {
        call(
                () ->
                        stub.withDeadlineAfter(
                                        properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                                .deleteQualification(
                                        DeleteQualificationRequest.newBuilder()
                                                .setQualificationId(qualificationId.toString())
                                                .setActor("hr")
                                                .build()));
    }

    public List<DictionaryModels.Relation> listRelations(int limit, int offset) {
        var response =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .listRelations(
                                                ListRelationsRequest.newBuilder()
                                                        .setLimit(limit)
                                                        .setOffset(offset)
                                                        .build()));
        return response.getItemsList().stream()
                .map(
                        item ->
                                new DictionaryModels.Relation(
                                        UUID.fromString(item.getLeftSkillId()),
                                        UUID.fromString(item.getRightSkillId()),
                                        item.getKind().name().replace("RELATION_KIND_", "")))
                .toList();
    }

    public DictionaryModels.Relation createRelation(
            UUID leftSkillId, UUID rightSkillId, String kind) {
        var item =
                call(
                        () ->
                                stub.withDeadlineAfter(
                                                properties.getDeadline().toMillis(),
                                                TimeUnit.MILLISECONDS)
                                        .createRelation(
                                                CreateRelationRequest.newBuilder()
                                                        .setLeftSkillId(leftSkillId.toString())
                                                        .setRightSkillId(rightSkillId.toString())
                                                        .setKind(relationKind(kind))
                                                        .setActor("hr")
                                                        .build()));
        return new DictionaryModels.Relation(
                UUID.fromString(item.getLeftSkillId()),
                UUID.fromString(item.getRightSkillId()),
                item.getKind().name().replace("RELATION_KIND_", ""));
    }

    public void deleteRelation(UUID leftSkillId, UUID rightSkillId) {
        call(
                () ->
                        stub.withDeadlineAfter(
                                        properties.getDeadline().toMillis(), TimeUnit.MILLISECONDS)
                                .deleteRelation(
                                        DeleteRelationRequest.newBuilder()
                                                .setLeftSkillId(leftSkillId.toString())
                                                .setRightSkillId(rightSkillId.toString())
                                                .setActor("hr")
                                                .build()));
    }

    private <T> T call(java.util.concurrent.Callable<T> action) {
        try {
            return action.call();
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE
                    || exception.getStatus().getCode() == io.grpc.Status.Code.DEADLINE_EXCEEDED) {
                throw new DictionaryUnavailableException(
                        exception.getStatus().getCode()
                                + ": "
                                + exception.getStatus().getDescription(),
                        exception);
            }
            throw new DictionaryRpcException(
                    exception.getStatus().getCode(), exception.getStatus().getDescription());
        } catch (Exception exception) {
            throw new DictionaryUnavailableException(exception.getMessage(), exception);
        }
    }

    private static ResolveSource source(String source) {
        return switch (source == null ? "" : source) {
            case "RESUME" -> ResolveSource.RESOLVE_SOURCE_RESUME;
            case "VACANCY" -> ResolveSource.RESOLVE_SOURCE_VACANCY;
            default -> ResolveSource.RESOLVE_SOURCE_ADMIN;
        };
    }

    private static SkillType skillType(String type) {
        return switch (type == null ? "" : type) {
            case "" -> SkillType.SKILL_TYPE_UNSPECIFIED;
            case "TECH" -> SkillType.SKILL_TYPE_TECH;
            case "TOOL" -> SkillType.SKILL_TYPE_TOOL;
            case "DOMAIN" -> SkillType.SKILL_TYPE_DOMAIN;
            case "SOFT" -> SkillType.SKILL_TYPE_SOFT;
            default -> SkillType.SKILL_TYPE_OTHER;
        };
    }

    private static RelationKind relationKind(String kind) {
        return switch (kind == null ? "" : kind) {
            case "SAME_GROUP" -> RelationKind.RELATION_KIND_SAME_GROUP;
            case "PARENT" -> RelationKind.RELATION_KIND_PARENT;
            case "CHILD" -> RelationKind.RELATION_KIND_CHILD;
            default -> RelationKind.RELATION_KIND_RELATED;
        };
    }

    private static ReviewStatus reviewStatus(String status) {
        return switch (status == null ? "" : status) {
            case "RESOLVED_ALIAS" -> ReviewStatus.REVIEW_STATUS_RESOLVED_ALIAS;
            case "RESOLVED_NEW_SKILL" -> ReviewStatus.REVIEW_STATUS_RESOLVED_NEW_SKILL;
            case "REJECTED" -> ReviewStatus.REVIEW_STATUS_REJECTED;
            default -> ReviewStatus.REVIEW_STATUS_PENDING;
        };
    }

    private static ReviewAction reviewAction(String action) {
        return switch (action == null ? "" : action) {
            case "NEW_SKILL" -> ReviewAction.REVIEW_ACTION_NEW_SKILL;
            case "REJECT" -> ReviewAction.REVIEW_ACTION_REJECT;
            default -> ReviewAction.REVIEW_ACTION_ALIAS;
        };
    }

    private static DictionaryModels.Skill toSkill(ats.dictionary.proto.Skill skill) {
        return new DictionaryModels.Skill(
                UUID.fromString(skill.getId()),
                skill.getCanonicalName(),
                skill.getSkillType().name().replace("SKILL_TYPE_", ""),
                skill.getStatus().name().replace("SKILL_STATUS_", ""),
                skill.getAliasesList().stream().map(DictionaryClient::toAlias).toList());
    }

    private static DictionaryModels.Alias toAlias(Alias alias) {
        return new DictionaryModels.Alias(
                UUID.fromString(alias.getId()),
                UUID.fromString(alias.getSkillId()),
                alias.getRaw(),
                alias.getIsCanonical(),
                alias.getLang(),
                alias.getSource().name().replace("ALIAS_SOURCE_", ""),
                alias.getLemmaMode().name().replace("LEMMA_MODE_", ""),
                alias.getCreatedBy(),
                alias.getCreatedAt());
    }

    private static DictionaryModels.Qualification toQualification(
            ats.dictionary.proto.Qualification item) {
        return new DictionaryModels.Qualification(
                UUID.fromString(item.getId()),
                item.getCanonicalName(),
                item.getStatus(),
                item.getQualificationType(),
                item.getAliasesList().stream()
                        .map(DictionaryClient::toQualificationAlias)
                        .toList());
    }

    private static DictionaryModels.QualificationAlias toQualificationAlias(
            ats.dictionary.proto.QualificationAlias alias) {
        return new DictionaryModels.QualificationAlias(
                UUID.fromString(alias.getId()),
                UUID.fromString(alias.getQualificationId()),
                alias.getRaw(),
                alias.getIsCanonical(),
                alias.getLang(),
                alias.getSource().name().replace("ALIAS_SOURCE_", ""),
                alias.getLemmaMode().name().replace("LEMMA_MODE_", ""),
                alias.getCreatedBy(),
                alias.getCreatedAt());
    }

    private static DictionaryModels.Review toReview(ats.dictionary.proto.ReviewItem item) {
        return new DictionaryModels.Review(
                UUID.fromString(item.getId()),
                item.getKey(),
                item.getSampleMaskedList(),
                item.getOccurrences(),
                item.getStatus().name().replace("REVIEW_STATUS_", ""),
                item.getSuggestedSkillId().isBlank()
                        ? null
                        : UUID.fromString(item.getSuggestedSkillId()),
                item.getSuggestedConfidence(),
                item.getCandidatesList().stream()
                        .map(
                                candidate ->
                                        new DictionaryModels.Candidate(
                                                UUID.fromString(candidate.getSkillId()),
                                                candidate.getCanonicalName(),
                                                candidate.getConfidence()))
                        .toList(),
                item.getResolvedSkillId().isBlank()
                        ? null
                        : UUID.fromString(item.getResolvedSkillId()),
                item.getResolvedBy(),
                item.getResolvedAt());
    }
}
