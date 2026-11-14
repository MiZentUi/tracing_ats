package ats.vacancy.application;

import ats.vacancy.template.Kind;
import ats.vacancy.template.KindSource;
import ats.vacancy.template.NormalizationStatus;
import ats.vacancy.template.SkillKindHistory;
import java.math.BigDecimal;
import java.util.UUID;

public record RequirementView(
        UUID id,
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
        boolean qualification,
        String educationLevel,
        String educationSpecialty) {}
