package ats.vacancy.dictionary;

import java.util.List;
import java.util.UUID;

/** Доменные представления dictionary-service, без protobuf в контроллерах. */
public final class DictionaryModels {

    private DictionaryModels() {}

    public record Candidate(UUID skillId, String canonicalName, double confidence) {}

    public record Resolved(
            String rawName,
            String normalizedKey,
            String foldedKey,
            UUID skillId,
            String canonicalName,
            String method,
            double confidence,
            List<Candidate> candidates) {}

    public record Alias(
            UUID id,
            UUID skillId,
            String raw,
            boolean canonical,
            String lang,
            String source,
            String lemmaMode,
            String createdBy,
            String createdAt) {}

    public record Skill(
            UUID id, String canonicalName, String skillType, String status, List<Alias> aliases) {}

    public record QualificationAlias(
            UUID id,
            UUID qualificationId,
            String raw,
            boolean canonical,
            String lang,
            String source,
            String lemmaMode,
            String createdBy,
            String createdAt) {}

    public record Qualification(
            UUID id,
            String canonicalName,
            String status,
            String qualificationType,
            List<QualificationAlias> aliases) {}

    public record Relation(UUID leftSkillId, UUID rightSkillId, String kind) {}

    public record Review(
            UUID id,
            String key,
            List<String> samples,
            long occurrences,
            String status,
            UUID suggestedSkillId,
            Double suggestedConfidence,
            List<Candidate> candidates,
            UUID resolvedSkillId,
            String resolvedBy,
            String resolvedAt) {}

    public record ReviewResult(Review review, Skill skill) {}

    public record ApplyAlias(String text, String lang) {}

    public record ApplySkill(
            String canonical, String skillType, String targetSkillId, List<ApplyAlias> aliases) {}

    public record Applied(List<Skill> skills, String resolverVersion) {}
}
