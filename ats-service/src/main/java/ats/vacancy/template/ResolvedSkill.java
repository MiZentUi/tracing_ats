package ats.vacancy.template;

import java.util.UUID;

public record ResolvedSkill(
        String normalizedKey,
        NormalizationStatus status,
        UUID skillId,
        String name,
        String originalText) {}
