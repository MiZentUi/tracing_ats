package ats.vacancy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skill_alias")
@Getter
@Setter
@NoArgsConstructor
public class SkillAliasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alias_normalized", nullable = false, length = 128)
    private String aliasNormalized;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Column(nullable = false, length = 8)
    private String source;
}
