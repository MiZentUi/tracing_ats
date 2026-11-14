package ats.vacancy.hh;

import java.time.Instant;
import java.util.UUID;

/** Представление статуса публикации вакансии на hh.ru. */
public record HhPublicationView(
        UUID vacancyId,
        Status status,
        String hhVacancyId,
        String hhUrl,
        String errorCode,
        Instant publishedAt) {

    public enum Status {
        NOT_PUBLISHED,
        PENDING,
        PUBLISHED,
        FAILED,
        ARCHIVED
    }
}
