package ats.resume.domain.vacancy;

import java.util.List;
import java.util.UUID;

/** Порт к vacancy-service. Политика шорт-листа и шаблон живут там, не в resume-service. */
public interface VacancyClient {

    VacancyTemplate getTemplate(UUID vacancyId);

    /** Один вызов на весь пакет: top-N нельзя решить, глядя на одного кандидата. */
    List<ScreeningResult> decideScreening(UUID vacancyId, List<ScreeningCandidate> candidates);

    record ScreeningCandidate(
            UUID applicationId, double score, double scoreLow, double scoreHigh) {}

    record ScreeningResult(UUID applicationId, ScreeningDecision decision, boolean enrich) {}

    enum ScreeningDecision {
        SHORTLISTED,
        BORDERLINE,
        NOT_SHORTLISTED
    }
}
