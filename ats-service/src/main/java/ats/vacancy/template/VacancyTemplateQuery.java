package ats.vacancy.template;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Читает текущую версию шаблона напрямую из PostgreSQL. Отдельный слой нужен, чтобы gRPC-сервис не
 * знал SQL и не тянул JPA-сущности.
 */
@Repository
@RequiredArgsConstructor
public class VacancyTemplateQuery {

    private static final String TEMPLATE_SQL =
            """
            SELECT v.id, v.title, v.source_text, v.current_version,
                   t.id AS template_id, t.version_no, t.github_weight,
                   s.code AS specialization_code, l.code AS level_code,
                   w.w_m, w.w_n, w.w_f, t.llm_prompt_version, w.version AS weights_version
            FROM vacancy v
            JOIN vacancy_template_version t
              ON t.vacancy_id = v.id AND t.version_no = v.current_version
            JOIN specialization s ON s.id = t.specialization_id
            JOIN level l ON l.id = t.level_id
            JOIN scoring_weights w ON w.id = t.scoring_weights_id
            WHERE v.id = ?
            """;

    private static final String REQUIREMENTS_SQL =
            """
            SELECT r.id, COALESCE(s.name, r.skill_text, r.raw_text) AS requirement_text,
                   COALESCE(s.name, r.skill_text, '') AS skill_name,
                   -- Числового weight в схеме больше нет (009): важность задаёт kind.
                   CASE r.kind WHEN 'MUST' THEN 3 WHEN 'NICE' THEN 2 ELSE 1 END AS weight,
                   r.kind, r.critical, r.qualification,
                   r.years_required, COALESCE(r.half_life_years, 0) AS half_life_years
            FROM vacancy_requirement r
            LEFT JOIN skill s ON s.id = r.skill_id
            WHERE r.template_version_id = ?
            ORDER BY r.position
            """;

    private final JdbcTemplate jdbcTemplate;

    public Optional<VacancyTemplateData> findCurrent(UUID vacancyId) {
        List<VacancyTemplateData> rows =
                jdbcTemplate.query(
                        TEMPLATE_SQL,
                        (rs, rowNum) -> {
                            UUID templateId = rs.getObject("template_id", UUID.class);
                            List<VacancyTemplateData.RequirementData> requirements =
                                    requirements(templateId);
                            int tenure =
                                    (int)
                                            Math.round(
                                                    requirements.stream()
                                                            .mapToDouble(
                                                                    VacancyTemplateData
                                                                                    .RequirementData
                                                                            ::yearsRequired)
                                                            .max()
                                                            .orElse(0));
                            return new VacancyTemplateData(
                                    rs.getObject("id", UUID.class),
                                    rs.getInt("version_no"),
                                    rs.getString("title"),
                                    rs.getString("specialization_code"),
                                    rs.getString("level_code"),
                                    rs.getString("source_text"),
                                    tenure,
                                    rs.getDouble("github_weight"),
                                    rs.getDouble("w_m"),
                                    rs.getDouble("w_n"),
                                    rs.getDouble("w_f"),
                                    rs.getString("llm_prompt_version"),
                                    "scoring-v2",
                                    "v" + rs.getInt("weights_version"),
                                    requirements);
                        },
                        vacancyId);
        return rows.stream().findFirst();
    }

    private List<VacancyTemplateData.RequirementData> requirements(UUID templateId) {
        return jdbcTemplate.query(
                REQUIREMENTS_SQL,
                (rs, rowNum) ->
                        new VacancyTemplateData.RequirementData(
                                rs.getObject("id", UUID.class),
                                rs.getString("requirement_text"),
                                rs.getString("skill_name"),
                                rs.getString("kind"),
                                rs.getInt("weight"),
                                rs.getBoolean("critical"),
                                rs.getDouble("years_required"),
                                rs.getDouble("half_life_years"),
                                rs.getBoolean("qualification")),
                templateId);
    }
}
