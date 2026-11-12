package ats.resume.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "screening_batch")
public class ScreeningBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vacancy_id")
    private UUID vacancyId;

    /** Направление «вакансии под резюме» опирается на резюме, а не на вакансию. */
    @Column(name = "resume_id")
    private UUID resumeId;

    @Column(nullable = false, length = 32)
    private String direction = "VACANCY_TO_CANDIDATES";

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int processed;

    @Column(nullable = false)
    private int failed;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
