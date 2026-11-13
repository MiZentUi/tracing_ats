package ats.vacancy.infrastructure.persistence;

import ats.vacancy.dictionary.DictionaryClient;
import ats.vacancy.dictionary.DictionaryModels;
import ats.vacancy.dictionary.DictionaryProperties;
import ats.vacancy.dictionary.DictionaryRpcException;
import ats.vacancy.dictionary.DictionaryUnavailableException;
import ats.vacancy.template.NormalizationStatus;
import ats.vacancy.template.ResolvedSkill;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Создание новых навыков. При включённом словаре владелец id — dictionary-service. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewSkillWriter {

    private final JdbcTemplate jdbcTemplate;
    private final SkillRepository skillRepository;
    private final DictionaryClient dictionaryClient;
    private final DictionaryProperties dictionaryProperties;

    @Transactional
    public ResolvedSkill ensureSkill(ResolvedSkill resolved) {
        if (resolved.status() != NormalizationStatus.NEW || resolved.skillId() != null) {
            return resolved;
        }
        if (dictionaryProperties.isEnabled()) {
            try {
                return ensureInDictionary(resolved);
            } catch (DictionaryUnavailableException | DictionaryRpcException exception) {
                log.warn(
                        "dictionary_unavailable fallback=local action=create_skill error={}",
                        exception.getMessage());
            }
        }
        return ensureLocal(resolved, UUID.randomUUID(), resolved.name());
    }

    /** Локальное зеркало навыка из словаря: FK в ats остаются рабочими. */
    @Transactional
    public ResolvedSkill ensureMirror(ResolvedSkill resolved) {
        if (resolved.skillId() == null) {
            return resolved;
        }
        Optional<SkillEntity> byId = skillRepository.findById(resolved.skillId());
        if (byId.isPresent()) {
            SkillEntity skill = byId.get();
            return new ResolvedSkill(
                    resolved.normalizedKey(),
                    NormalizationStatus.MATCHED,
                    skill.getId(),
                    skill.getName(),
                    resolved.originalText());
        }
        Optional<SkillEntity> byName =
                skillRepository.findByNormalizedName(resolved.normalizedKey());
        if (byName.isPresent()) {
            SkillEntity skill = byName.get();
            return new ResolvedSkill(
                    resolved.normalizedKey(),
                    NormalizationStatus.MATCHED,
                    skill.getId(),
                    skill.getName(),
                    resolved.originalText());
        }
        return ensureLocal(resolved, resolved.skillId(), resolved.name());
    }

    private ResolvedSkill ensureInDictionary(ResolvedSkill resolved) {
        DictionaryModels.Skill skill = dictionaryClient.createSkill(resolved.name(), "OTHER");
        String raw = resolved.originalText() == null ? resolved.name() : resolved.originalText();
        if (!raw.equalsIgnoreCase(skill.canonicalName())) {
            dictionaryClient.addAlias(skill.id(), raw, "mixed", "AUTO");
        }
        return ensureLocal(resolved, skill.id(), skill.canonicalName());
    }

    private ResolvedSkill ensureLocal(ResolvedSkill resolved, UUID id, String name) {
        jdbcTemplate.update(
                "INSERT INTO skill (id, name, normalized_name) VALUES (?::uuid, ?, ?) "
                        + "ON CONFLICT (normalized_name) DO NOTHING",
                id.toString(),
                name,
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
