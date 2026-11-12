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

/** Неизменяемый снимок `ParsedResume` из scoring-service. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "resume_extraction")
public class ResumeExtractionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "extracted_features", nullable = false, columnDefinition = "text")
    private String extractedFeatures;

    @Column(name = "extraction_version", nullable = false, length = 32)
    private String extractionVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
