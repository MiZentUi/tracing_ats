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

/** Черновик письма кандидату. Не содержит PII и не отправляется автоматически. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "candidate_message")
public class CandidateMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(name = "score_run_id", nullable = false)
    private UUID scoreRunId;

    @Column(nullable = false, length = 16)
    private String phase;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;

    @Column(name = "model_version", length = 128)
    private String modelVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
