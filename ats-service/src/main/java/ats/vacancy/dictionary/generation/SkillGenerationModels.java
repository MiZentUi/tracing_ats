package ats.vacancy.dictionary.generation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTO админской генерации навыков. */
public final class SkillGenerationModels {

    private SkillGenerationModels() {}

    public record GenerationJob(
            UUID id,
            String specialization,
            String note,
            String status,
            String model,
            String promptVersion,
            int maxSkills,
            String error,
            Instant createdAt,
            Instant finishedAt,
            List<SkillDraft> skills) {}

    public record SkillDraft(
            UUID id,
            String canonical,
            String skillType,
            UUID targetSkillId,
            String targetSkillName,
            UUID conflictSkillId,
            String conflictSkillName,
            String status,
            List<AliasDraft> aliases) {}

    public record AliasDraft(
            UUID id,
            String text,
            String lang,
            String category,
            UUID conflictSkillId,
            String conflictSkillName,
            String status) {}

    public record AliasInput(
            String text,
            String lang,
            String category,
            UUID conflictSkillId,
            String conflictSkillName) {}

    public record SkillInput(
            String canonical,
            String skillType,
            UUID targetSkillId,
            String targetSkillName,
            UUID conflictSkillId,
            String conflictSkillName,
            List<AliasInput> aliases) {}

    public record AcceptedSkill(
            UUID draftId,
            String canonical,
            String skillType,
            UUID targetSkillId,
            List<String> aliases) {}

    public record ApproveRequest(List<AcceptedSkill> skills) {}
}
