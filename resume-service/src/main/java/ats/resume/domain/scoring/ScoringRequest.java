package ats.resume.domain.scoring;

import ats.resume.domain.vacancy.VacancyTemplate;
import java.util.UUID;

/**
 * Запрос на скоринг. Текст уже обезличен и очищен от prompt injection. Образование и внешние факты
 * в текущем gRPC-контракте не передаются: scoring-service получает только текст и шаблон.
 */
public record ScoringRequest(UUID applicationId, String maskedText, VacancyTemplate template) {}
