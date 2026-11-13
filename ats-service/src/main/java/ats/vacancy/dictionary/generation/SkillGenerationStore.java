package ats.vacancy.dictionary.generation;

import ats.vacancy.dictionary.generation.SkillGenerationModels.AliasDraft;
import ats.vacancy.dictionary.generation.SkillGenerationModels.GenerationJob;
import ats.vacancy.dictionary.generation.SkillGenerationModels.SkillDraft;
import ats.vacancy.dictionary.generation.SkillGenerationModels.SkillInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Staging-хранилище черновиков генерации в public-схеме ats. */
@Component
@RequiredArgsConstructor
public class SkillGenerationStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UUID createJob(
            String specialization,
            String note,
            int maxSkills,
            String actor,
            String model,
            String promptVersion) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO skill_generation_job
                    (specialization, note, status, requested_by, model, prompt_version, max_skills)
                VALUES (?, ?, 'PENDING', ?, ?, ?, ?)
                RETURNING id
                """,
                UUID.class,
                specialization,
                note,
                actor,
                model,
                promptVersion,
                maxSkills);
    }

    public void markRunning(UUID jobId) {
        jdbcTemplate.update(
                """
                UPDATE skill_generation_job
                SET status = 'RUNNING', started_at = now()
                WHERE id = ?::uuid AND status = 'PENDING'
                """,
                jobId.toString());
    }

    public void markFailed(UUID jobId, String error) {
        jdbcTemplate.update(
                """
                UPDATE skill_generation_job
                SET status = 'FAILED', error = ?, finished_at = now()
                WHERE id = ?::uuid
                """,
                error,
                jobId.toString());
    }

    @Transactional
    public void saveDrafts(UUID jobId, List<SkillInput> skills, String rawResponse) {
        jdbcTemplate.update(
                "DELETE FROM skill_generation_draft WHERE job_id = ?::uuid", jobId.toString());
        int skillPosition = 0;
        for (SkillInput skill : skills) {
            UUID draftId =
                    jdbcTemplate.queryForObject(
                            """
                            INSERT INTO skill_generation_draft
                                (job_id, position, canonical, skill_type, target_skill_id,
                                 target_skill_name, conflict_skill_id, conflict_skill_name)
                            VALUES (?::uuid, ?, ?, ?, ?::uuid, ?, ?::uuid, ?)
                            RETURNING id
                            """,
                            UUID.class,
                            jobId.toString(),
                            skillPosition++,
                            skill.canonical(),
                            skill.skillType(),
                            skill.targetSkillId() == null ? null : skill.targetSkillId().toString(),
                            skill.targetSkillName(),
                            skill.conflictSkillId() == null
                                    ? null
                                    : skill.conflictSkillId().toString(),
                            skill.conflictSkillName());
            int aliasPosition = 0;
            for (var alias : skill.aliases()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO skill_generation_alias
                            (draft_id, position, alias_raw, lang, category,
                             conflict_skill_id, conflict_skill_name)
                        VALUES (?::uuid, ?, ?, ?, ?, ?::uuid, ?)
                        """,
                        draftId.toString(),
                        aliasPosition++,
                        alias.text(),
                        alias.lang(),
                        alias.category(),
                        alias.conflictSkillId() == null ? null : alias.conflictSkillId().toString(),
                        alias.conflictSkillName());
            }
        }
        jdbcTemplate.update(
                """
                UPDATE skill_generation_job
                SET status = 'DONE', raw_response = ?::jsonb, finished_at = now(), error = null
                WHERE id = ?::uuid
                """,
                json(rawResponse),
                jobId.toString());
    }

    public Optional<GenerationJob> find(UUID jobId) {
        List<GenerationJob> jobs =
                jdbcTemplate.query(
                        "SELECT * FROM skill_generation_job WHERE id = ?::uuid",
                        (rs, rowNum) -> job(rs),
                        jobId.toString());
        if (jobs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(withDrafts(jobs.getFirst()));
    }

    public List<GenerationJob> list(int limit) {
        List<GenerationJob> jobs =
                jdbcTemplate.query(
                        """
                        SELECT * FROM skill_generation_job
                        ORDER BY created_at DESC
                        LIMIT ?
                        """,
                        (rs, rowNum) -> job(rs),
                        Math.max(1, Math.min(limit, 50)));
        return jobs.stream().map(this::withDrafts).toList();
    }

    public void markApproved(UUID jobId, List<UUID> skillDraftIds, List<UUID> aliasDraftIds) {
        jdbcTemplate.update(
                "UPDATE skill_generation_draft SET status = 'REJECTED' WHERE job_id = ?::uuid",
                jobId.toString());
        for (UUID id : skillDraftIds) {
            jdbcTemplate.update(
                    "UPDATE skill_generation_draft SET status = 'APPROVED' WHERE id = ?::uuid",
                    id.toString());
        }
        jdbcTemplate.update(
                """
                UPDATE skill_generation_alias SET status = 'REJECTED'
                WHERE draft_id IN (SELECT id FROM skill_generation_draft WHERE job_id = ?::uuid)
                """,
                jobId.toString());
        for (UUID id : aliasDraftIds) {
            jdbcTemplate.update(
                    "UPDATE skill_generation_alias SET status = 'APPROVED' WHERE id = ?::uuid",
                    id.toString());
        }
        jdbcTemplate.update(
                """
                UPDATE skill_generation_job
                SET status = 'APPROVED', finished_at = now()
                WHERE id = ?::uuid
                """,
                jobId.toString());
    }

    public void markRejected(UUID jobId) {
        jdbcTemplate.update(
                """
                UPDATE skill_generation_job
                SET status = 'REJECTED', finished_at = now()
                WHERE id = ?::uuid
                """,
                jobId.toString());
    }

    private GenerationJob withDrafts(GenerationJob job) {
        List<SkillDraft> drafts =
                jdbcTemplate.query(
                        """
                        SELECT * FROM skill_generation_draft
                        WHERE job_id = ?::uuid ORDER BY position
                        """,
                        (rs, rowNum) -> draft(rs),
                        job.id().toString());
        Map<UUID, List<AliasDraft>> aliases = aliases(job.id());
        List<SkillDraft> withAliases =
                drafts.stream()
                        .map(
                                draft ->
                                        new SkillDraft(
                                                draft.id(),
                                                draft.canonical(),
                                                draft.skillType(),
                                                draft.targetSkillId(),
                                                draft.targetSkillName(),
                                                draft.conflictSkillId(),
                                                draft.conflictSkillName(),
                                                draft.status(),
                                                aliases.getOrDefault(draft.id(), List.of())))
                        .toList();
        return new GenerationJob(
                job.id(),
                job.specialization(),
                job.note(),
                job.status(),
                job.model(),
                job.promptVersion(),
                job.maxSkills(),
                job.error(),
                job.createdAt(),
                job.finishedAt(),
                withAliases);
    }

    private Map<UUID, List<AliasDraft>> aliases(UUID jobId) {
        Map<UUID, List<AliasDraft>> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT a.* FROM skill_generation_alias a
                JOIN skill_generation_draft d ON d.id = a.draft_id
                WHERE d.job_id = ?::uuid
                ORDER BY d.position, a.position
                """,
                rs -> {
                    UUID draftId = rs.getObject("draft_id", UUID.class);
                    result.computeIfAbsent(draftId, key -> new ArrayList<>()).add(alias(rs));
                },
                jobId.toString());
        return result;
    }

    private static GenerationJob job(ResultSet rs) throws SQLException {
        return new GenerationJob(
                rs.getObject("id", UUID.class),
                rs.getString("specialization"),
                rs.getString("note"),
                rs.getString("status"),
                rs.getString("model"),
                rs.getString("prompt_version"),
                rs.getInt("max_skills"),
                rs.getString("error"),
                instant(rs, "created_at"),
                instant(rs, "finished_at"),
                List.of());
    }

    private static SkillDraft draft(ResultSet rs) throws SQLException {
        return new SkillDraft(
                rs.getObject("id", UUID.class),
                rs.getString("canonical"),
                rs.getString("skill_type"),
                rs.getObject("target_skill_id", UUID.class),
                rs.getString("target_skill_name"),
                rs.getObject("conflict_skill_id", UUID.class),
                rs.getString("conflict_skill_name"),
                rs.getString("status"),
                List.of());
    }

    private static AliasDraft alias(ResultSet rs) throws SQLException {
        return new AliasDraft(
                rs.getObject("id", UUID.class),
                rs.getString("alias_raw"),
                rs.getString("lang"),
                rs.getString("category"),
                rs.getObject("conflict_skill_id", UUID.class),
                rs.getString("conflict_skill_name"),
                rs.getString("status"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String json(String value) {
        try {
            return objectMapper.writeValueAsString(value == null ? "" : value);
        } catch (JsonProcessingException exception) {
            return "\"\"";
        }
    }
}
