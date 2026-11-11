package ats.vacancy.infrastructure.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL для счётчиков типов: только атомарный upsert, без read-modify-write. */
@Repository
@RequiredArgsConstructor
public class SkillKindStatRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Прибавляет дельты и возвращает счётчики после обновления: must, nice, optional.
     *
     * <p>Один {@code INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING} вместо JPA: параллельные
     * сохранения не теряют инкременты, а pessimistic lock или повторные чтения не нужны. JPA UPSERT
     * не выражает, поэтому здесь осознанно JdbcTemplate.
     */
    public int[] applyDelta(
            UUID specializationId,
            UUID levelId,
            UUID skillId,
            int mustDelta,
            int niceDelta,
            int optionalDelta) {
        return jdbcTemplate.queryForObject(
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
                RETURNING must_count, nice_count, optional_count
                """,
                (rs, rowNum) ->
                        new int[] {
                            rs.getInt("must_count"),
                            rs.getInt("nice_count"),
                            rs.getInt("optional_count")
                        },
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
