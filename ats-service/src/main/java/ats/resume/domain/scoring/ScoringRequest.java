package ats.resume.domain.scoring;

import ats.resume.domain.vacancy.VacancyTemplate;
import java.util.UUID;

/**
 * Запрос на скоринг. Текст уже обезличен и очищен от prompt injection. `parsed` — готовый
 * `ParsedResume` из scoring-service: модель разбирает резюме один раз, дальше считает без LLM.
 */
public record ScoringRequest(
        UUID applicationId, String maskedText, VacancyTemplate template, ParsedResume parsed) {}
