package ats.vacancy.application;

import java.util.List;
import java.util.UUID;

public record VacancyView(
        UUID id,
        String specialization,
        String level,
        String title,
        Integer experienceYears,
        String status,
        String sourceText,
        int version,
        boolean confirmed,
        List<RequirementView> requirements,
        String model,
        String promptVersion,
        int droppedRequirements) {}
