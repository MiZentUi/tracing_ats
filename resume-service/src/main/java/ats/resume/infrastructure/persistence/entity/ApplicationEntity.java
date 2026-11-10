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
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "application")
public class ApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "consent_github", nullable = false)
    private boolean consentGithub;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "screening_decision", length = 16)
    private String screeningDecision;

    @Column(name = "preliminary_score_run_id")
    private UUID preliminaryScoreRunId;

    @Column(name = "final_score_run_id")
    private UUID finalScoreRunId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
