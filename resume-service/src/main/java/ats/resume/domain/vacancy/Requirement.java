package ats.resume.domain.vacancy;

import java.util.UUID;

/** Требование вакансии в доменном виде. Все числа приходят из шаблона, не считаются в Java. */
public record Requirement(
        UUID id,
        String text,
        String skill,
        RequirementKind kind,
        int weight,
        boolean critical,
        double yearsRequired,
        double halfLifeYears) {}
