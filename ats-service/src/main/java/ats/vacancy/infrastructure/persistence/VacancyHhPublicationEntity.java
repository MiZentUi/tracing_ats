package ats.vacancy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Привязка вакансии к hh.ru и статус публикации. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vacancy_hh_publication")
public class VacancyHhPublicationEntity {

    @Id
    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "hh_draft_id", length = 64)
    private String hhDraftId;

    @Column(name = "hh_vacancy_id", length = 64)
    private String hhVacancyId;

    @Column(name = "hh_url", length = 512)
    private String hhUrl;

    @Column(name = "hh_employer_id", length = 64)
    private String hhEmployerId;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
