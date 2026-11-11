package ats.vacancy.infrastructure.persistence;

import ats.vacancy.template.NormalizationStatus;
import ats.vacancy.template.ResolvedSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Создание новых навыков и алиаса на себя. Гонки не должны давать ошибку. */
@Component
@RequiredArgsConstructor
public class NewSkillWriter {

    private final JdbcTemplate jdbcTemplate;
    private final SkillRepository skillRepository;

    @Transactional
    public ResolvedSkill ensureSkill(ResolvedSkill resolved) {
        if (resolved.status() != NormalizationStatus.NEW || resolved.skillId() != null) {
            return resolved;
        }
        jdbcTemplate.update(
                "INSERT INTO skill (name, normalized_name) VALUES (?, ?) "
                        + "ON CONFLICT (normalized_name) DO NOTHING",
                resolved.name(),
                resolved.normalizedKey());
        jdbcTemplate.update(
                "INSERT INTO skill_alias (alias_normalized, skill_id, source) "
                        + "SELECT ?, id, 'HR' FROM skill WHERE normalized_name = ? "
                        + "ON CONFLICT (alias_normalized) DO NOTHING",
                resolved.normalizedKey(),
                resolved.normalizedKey());

        SkillEntity skill =
                skillRepository
                        .findByNormalizedName(resolved.normalizedKey())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Cannot create skill " + resolved.normalizedKey()));
        return new ResolvedSkill(
                resolved.normalizedKey(),
                NormalizationStatus.MATCHED,
                skill.getId(),
                skill.getName(),
                resolved.originalText());
    }
}
