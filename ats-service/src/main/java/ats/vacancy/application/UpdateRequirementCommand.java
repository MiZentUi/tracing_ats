package ats.vacancy.application;

import ats.vacancy.template.Kind;
import java.math.BigDecimal;

public record UpdateRequirementCommand(
        String id,
        String skill,
        Kind kind,
        BigDecimal yearsRequired,
        String quote,
        String clarityWarning) {}
