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
@Table(name = "requirement_result")
public class RequirementResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "score_run_id", nullable = false)
    private UUID scoreRunId;

    @Column(name = "requirement_id", nullable = false, length = 128)
    private String requirementId;

    @Column(name = "requirement_text", nullable = false, columnDefinition = "text")
    private String requirementText;

    @Column(nullable = false, length = 8)
    private String kind;

    @Column(nullable = false)
    private int weight;

    @Column(nullable = false)
    private boolean critical;

    /** Релевантность требования (`R_i`) из scoring-service. */
    @Column(precision = 4, scale = 3)
    private BigDecimal relevance;

    /** Покрытие требования (`c_i`). Историческое имя колонки — c_r. */
    @Column(name = "c_r", nullable = false, precision = 4, scale = 3)
    private BigDecimal cR;

    @Column(nullable = false, length = 8)
    private String status;

    /** Стаж по требованию, если scoring-service его вернул. */
    private Integer tenure;

    // Поля старой локальной модели. Новые запуски их не заполняют.
    @Column(precision = 4, scale = 3)
    private BigDecimal m;

    @Column(precision = 4, scale = 3)
    private BigDecimal t;

    @Column(precision = 4, scale = 3)
    private BigDecimal rho;

    @Column(name = "evidence_source", length = 16)
    private String evidenceSource;

    @Column(name = "evidence_quote", columnDefinition = "text")
    private String evidenceQuote;

    @Column(columnDefinition = "text")
    private String gap;

    @Column(nullable = false)
    private boolean external;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
