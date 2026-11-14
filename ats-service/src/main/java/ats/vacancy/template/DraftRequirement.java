package ats.vacancy.template;

import java.math.BigDecimal;
import java.util.UUID;

public record DraftRequirement(
        String skill,
        UUID skillId,
        NormalizationStatus normalizationStatus,
        Kind kind,
        KindSource kindSource,
        Kind llmKind,
        BigDecimal yearsRequired,
        String quote,
        String clarityWarning,
        SkillKindHistory history,
        String normalizedKey,
        boolean qualification) {

    public DraftRequirement withKindAndHistory(
            Kind newKind, KindSource newSource, Kind newLlmKind, SkillKindHistory newHistory) {
        return new DraftRequirement(
                skill,
                skillId,
                normalizationStatus,
                newKind,
                newSource,
                newLlmKind,
                yearsRequired,
                quote,
                clarityWarning,
                newHistory,
                normalizedKey,
                qualification);
    }
}
