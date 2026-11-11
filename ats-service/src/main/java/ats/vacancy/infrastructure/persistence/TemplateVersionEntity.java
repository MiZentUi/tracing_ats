package ats.vacancy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vacancy_template_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "specialization_id", nullable = false)
    private UUID specializationId;

    @Column(name = "level_id", nullable = false)
    private UUID levelId;

    @Column(name = "github_weight", nullable = false, precision = 4, scale = 3)
    private BigDecimal githubWeight;

    @Column(name = "scoring_weights_id", nullable = false)
    private UUID scoringWeightsId;

    @Column(name = "llm_prompt_version", length = 32)
    private String llmPromptVersion;

    @Column(name = "llm_model", length = 128)
    private String llmModel;

    @Column(name = "dropped_requirements", nullable = false)
    private int droppedRequirements;

    @Column(name = "is_confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
