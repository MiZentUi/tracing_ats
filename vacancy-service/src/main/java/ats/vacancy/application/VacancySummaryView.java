package ats.vacancy.application;

import java.time.Instant;
import java.util.UUID;

public record VacancySummaryView(
        UUID id,
        String title,
        String specialization,
        String level,
        String status,
        int version,
        boolean confirmed,
        int requirementsCount,
        Instant createdAt) {}
