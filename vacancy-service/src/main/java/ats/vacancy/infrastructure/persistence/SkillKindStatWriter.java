package ats.vacancy.infrastructure.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Атомарный upsert счётчиков. Схема «прочитал, прибавил, записал» запрещена. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillKindStatWriter {

    private final JdbcTemplate jdbcTemplate;

    public void applyDelta(
            UUID specializationId, UUID levelId, UUID skillId, String kind, int delta) {
        if (delta == 0) {
            return;
        }
        int mustDelta = "MUST".equals(kind) ? delta : 0;
        int niceDelta = "NICE".equals(kind) ? delta : 0;
        int optionalDelta = "OPTIONAL".equals(kind) ? delta : 0;

        if (delta < 0) {
            String column =
                    switch (kind) {
                        case "MUST" -> "must_count";
                        case "NICE" -> "nice_count";
                        case "OPTIONAL" -> "optional_count";
                        default -> throw new IllegalArgumentException("Unknown kind: " + kind);
                    };
            Integer current =
                    jdbcTemplate.queryForObject(
                            "SELECT "
                                    + column
                                    + " FROM skill_kind_stat WHERE specialization_id = ? "
                                    + "AND level_id = ? AND skill_id = ?",
                            Integer.class,
                            specializationId,
                            levelId,
                            skillId);
            if (current != null && current < -delta) {
                log.warn(
                        "skill_kind_stat would be clamped at zero: specialization={}, "
                                + "level={}, skill={}, kind={}",
                        specializationId,
                        levelId,
                        skillId,
                        kind);
            }
        }

        jdbcTemplate.update(
                """
                INSERT INTO skill_kind_stat (
                    specialization_id, level_id, skill_id,
                    must_count, nice_count, optional_count, updated_at
                ) VALUES (?, ?, ?, GREATEST(?, 0), GREATEST(?, 0), GREATEST(?, 0), now())
                ON CONFLICT (specialization_id, level_id, skill_id) DO UPDATE SET
                    must_count = GREATEST(skill_kind_stat.must_count + ?, 0),
                    nice_count = GREATEST(skill_kind_stat.nice_count + ?, 0),
                    optional_count = GREATEST(skill_kind_stat.optional_count + ?, 0),
                    updated_at = now()
                """,
                specializationId,
                levelId,
                skillId,
                mustDelta,
                niceDelta,
                optionalDelta,
                mustDelta,
                niceDelta,
                optionalDelta);
    }
}
