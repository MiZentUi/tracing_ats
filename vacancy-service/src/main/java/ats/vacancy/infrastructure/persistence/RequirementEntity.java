package ats.vacancy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vacancy_requirement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequirementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Column(nullable = false)
    private Short position;

    @Column(name = "skill_id")
    private UUID skillId;

    @Column(name = "skill_text", length = 128)
    private String skillText;

    @Column(name = "raw_text")
    private String rawText;

    @Column(nullable = false, length = 8)
    private String kind;

    @Column(name = "kind_source", nullable = false, length = 8)
    private String kindSource;

    @Column(name = "llm_kind", length = 8)
    private String llmKind;

    @Column(nullable = false)
    private boolean critical;

    @Column(name = "years_required", nullable = false, precision = 4, scale = 1)
    private BigDecimal yearsRequired;

    @Column(name = "half_life_years", precision = 4, scale = 1)
    private BigDecimal halfLifeYears;

    @Column(name = "clarity_warning")
    private String clarityWarning;
}
