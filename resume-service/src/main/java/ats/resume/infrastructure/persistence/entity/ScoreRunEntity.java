package ats.resume.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "score_run")
public class ScoreRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(nullable = false, length = 16)
    private String phase;

    @Column(name = "scoring_version", nullable = false, length = 32)
    private String scoringVersion;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    @Column(name = "model_version", length = 128)
    private String modelVersion;

    @Column(name = "weights_version", length = 32)
    private String weightsVersion;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "score_low", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreLow;

    @Column(name = "score_high", nullable = false, precision = 5, scale = 2)
    private BigDecimal scoreHigh;

    @Column(name = "cap_applied", nullable = false)
    private boolean capApplied;

    @Column(nullable = false, columnDefinition = "text")
    private String flags;

    @Column(nullable = false, columnDefinition = "text")
    private String parsed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
