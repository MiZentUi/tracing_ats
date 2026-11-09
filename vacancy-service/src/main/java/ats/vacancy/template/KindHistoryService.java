package ats.vacancy.template;

import ats.vacancy.config.TemplateProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Подсказка типа из накопленной статистики HR. Статистика изолирована парой «специализация ×
 * уровень».
 */
@Component
@RequiredArgsConstructor
public class KindHistoryService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TemplateProperties templateProperties;

    public List<DraftRequirement> apply(
            List<DraftRequirement> requirements, UUID specializationId, UUID levelId) {
        Map<UUID, SkillKindHistory> stats = loadStats(specializationId, levelId, requirements);
        return requirements.stream()
                .map(
                        requirement -> {
                            if (requirement.skillId() == null) {
                                return requirement;
                            }
                            SkillKindHistory history = stats.get(requirement.skillId());
                            SkillKindHistory visibleHistory =
                                    history == null || history.isEmpty() ? null : history;
                            HistoryDecision decision = decide(requirement.kind(), history);
                            return requirement.withKindAndHistory(
                                    decision.kind(),
                                    decision.source(),
                                    decision.llmKind(),
                                    visibleHistory);
                        })
                .toList();
    }

    public Map<UUID, SkillKindHistory> loadStats(
            UUID specializationId, UUID levelId, List<DraftRequirement> requirements) {
        return loadStatsForSkills(
                specializationId,
                levelId,
                requirements.stream()
                        .map(DraftRequirement::skillId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList());
    }

    public Map<UUID, SkillKindHistory> loadStatsForSkills(
            UUID specializationId, UUID levelId, List<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SkillKindHistory> result = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT skill_id, must_count, nice_count, optional_count
                FROM skill_kind_stat
                WHERE specialization_id = :specializationId
                  AND level_id = :levelId
                  AND skill_id IN (:skillIds)
                """,
                Map.of(
                        "specializationId", specializationId,
                        "levelId", levelId,
                        "skillIds", skillIds),
                rs -> {
                    result.put(
                            rs.getObject("skill_id", UUID.class),
                            new SkillKindHistory(
                                    rs.getInt("must_count"),
                                    rs.getInt("nice_count"),
                                    rs.getInt("optional_count")));
                });
        return result;
    }

    public HistoryDecision decide(Kind llmKind, SkillKindHistory history) {
        TemplateProperties.KindHistory config = templateProperties.getKindHistory();
        if (history == null || history.total() < config.getMinObservations()) {
            return new HistoryDecision(llmKind, KindSource.LLM, llmKind, history);
        }

        int max = Math.max(history.must(), Math.max(history.nice(), history.optional()));
        int topCount = 0;
        Kind top = null;
        if (history.must() == max) {
            top = Kind.MUST;
            topCount++;
        }
        if (history.nice() == max) {
            top = top == null ? Kind.NICE : top;
            topCount++;
        }
        if (history.optional() == max) {
            top = top == null ? Kind.OPTIONAL : top;
            topCount++;
        }

        double share = (double) max / history.total();
        if (topCount != 1 || share < config.getMinShare()) {
            return new HistoryDecision(llmKind, KindSource.LLM, llmKind, history);
        }
        return new HistoryDecision(top, KindSource.HISTORY, llmKind, history);
    }
}
