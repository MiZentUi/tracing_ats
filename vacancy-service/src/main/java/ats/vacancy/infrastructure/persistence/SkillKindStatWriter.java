package ats.vacancy.infrastructure.persistence;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Переводит тип требования в дельту счётчика и пишет её атомарным upsert. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillKindStatWriter {

    private final SkillKindStatRepository repository;

    public void applyDelta(
            UUID specializationId, UUID levelId, UUID skillId, String kind, int delta) {
        if (delta == 0) {
            return;
        }

        int[] counts =
                switch (kind) {
                    case "MUST" ->
                            repository.applyDelta(specializationId, levelId, skillId, delta, 0, 0);
                    case "NICE" ->
                            repository.applyDelta(specializationId, levelId, skillId, 0, delta, 0);
                    case "OPTIONAL" ->
                            repository.applyDelta(specializationId, levelId, skillId, 0, 0, delta);
                    default -> throw new IllegalArgumentException("Unknown kind: " + kind);
                };

        if (delta < 0 && (counts[0] == 0 || counts[1] == 0 || counts[2] == 0)) {
            log.warn(
                    "skill_kind_stat clamped at zero: specialization={}, level={}, skill={}, kind={}",
                    specializationId,
                    levelId,
                    skillId,
                    kind);
        }
    }
}
