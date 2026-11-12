package ats.resume.domain.scoring;

import java.util.UUID;

/** Результат оценки одной вакансии в пакетном вызове. */
public record ScoringBatchItem(
        UUID vacancyId, ScoringResult result, String errorCode, String errorMessage) {

    public static ScoringBatchItem ok(UUID vacancyId, ScoringResult result) {
        return new ScoringBatchItem(vacancyId, result, null, null);
    }

    public static ScoringBatchItem failed(UUID vacancyId, String code, String message) {
        return new ScoringBatchItem(vacancyId, null, code, message);
    }

    public boolean isFailed() {
        return errorCode != null;
    }
}
