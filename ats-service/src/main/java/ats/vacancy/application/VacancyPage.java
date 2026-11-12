package ats.vacancy.application;

import java.util.List;

public record VacancyPage(List<VacancySummaryView> items, long total) {}
