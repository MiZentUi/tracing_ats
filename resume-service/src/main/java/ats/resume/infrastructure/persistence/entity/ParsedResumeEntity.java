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
@Table(name = "parsed_resume")
public class ParsedResumeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resume_id", nullable = false, unique = true)
    private UUID resumeId;

    @Column(name = "masked_text", nullable = false, columnDefinition = "text")
    private String maskedText;

    @Column(name = "injection_flags", nullable = false, columnDefinition = "text")
    private String injectionFlags;

    @Column(name = "education_level", length = 64)
    private String educationLevel;

    @Column(length = 128)
    private String degree;

    @Column(length = 256)
    private String specialty;

    @Column(name = "education_years", precision = 4, scale = 1)
    private BigDecimal educationYears;

    @Column(name = "pii_model", length = 128)
    private String piiModel;

    @Column(name = "pii_model_hash", length = 64)
    private String piiModelHash;

    @Column(name = "ner_used", nullable = false)
    private boolean nerUsed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
