package ats.resume.domain.ingestion;

import java.util.UUID;

/** HH-отклик успешно прошёл предварительный скоринг: можно отправить алерт. */
public record HhApplicationScoredEvent(
        UUID applicationId,
        UUID vacancyId,
        String vacancyTitle,
        double score,
        double scoreLow,
        double scoreHigh) {}
