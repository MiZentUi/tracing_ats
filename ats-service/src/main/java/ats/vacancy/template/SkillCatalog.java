package ats.vacancy.template;

import ats.vacancy.dictionary.DictionaryClient;
import ats.vacancy.dictionary.DictionaryModels;
import ats.vacancy.dictionary.DictionaryProperties;
import ats.vacancy.dictionary.DictionaryRpcException;
import ats.vacancy.dictionary.DictionaryUnavailableException;
import ats.vacancy.infrastructure.persistence.SkillAliasRepository;
import ats.vacancy.infrastructure.persistence.SkillEntity;
import ats.vacancy.infrastructure.persistence.SkillRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Словарь синонимов: dictionary-service, с локальным fallback на таблицы ats. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCatalog {

    private final SkillAliasRepository aliasRepository;
    private final SkillRepository skillRepository;
    private final DictionaryClient dictionaryClient;
    private final DictionaryProperties dictionaryProperties;

    @Transactional(readOnly = true)
    public Map<String, SkillRef> loadAliases() {
        Map<String, SkillRef> result = new HashMap<>();
        for (Object[] row : aliasRepository.findAllAliases()) {
            result.put((String) row[0], new SkillRef((UUID) row[1], (String) row[2]));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ResolvedSkill resolve(String rawSkill, Map<String, SkillRef> aliases) {
        if (rawSkill == null || rawSkill.isBlank()) {
            return new ResolvedSkill("", NormalizationStatus.NEW, null, null, null);
        }
        String original = rawSkill.strip();
        if (dictionaryProperties.isEnabled()) {
            try {
                return resolveWithDictionary(original);
            } catch (DictionaryUnavailableException | DictionaryRpcException exception) {
                log.warn("dictionary_unavailable fallback=local error={}", exception.getMessage());
            }
        }
        return resolveLocal(original, aliases);
    }

    private ResolvedSkill resolveWithDictionary(String original) {
        DictionaryModels.Resolved resolved = dictionaryClient.resolve(original, "VACANCY");
        String key = resolved.normalizedKey();
        if (resolved.skillId() == null) {
            return new ResolvedSkill(key, NormalizationStatus.NEW, null, original, original);
        }
        return new ResolvedSkill(
                key,
                NormalizationStatus.MATCHED,
                resolved.skillId(),
                resolved.canonicalName(),
                original);
    }

    private ResolvedSkill resolveLocal(String original, Map<String, SkillRef> aliases) {
        String key = SkillNormalizer.normalizeKey(original);
        SkillRef matched = aliases.get(key);
        if (matched != null) {
            return new ResolvedSkill(
                    key, NormalizationStatus.MATCHED, matched.skillId(), matched.name(), original);
        }

        // Защита от рассинхрона словаря: навык с таким normalized_name уже существует.
        Optional<SkillEntity> existing = skillRepository.findByNormalizedName(key);
        if (existing.isPresent()) {
            SkillEntity skill = existing.get();
            return new ResolvedSkill(
                    key, NormalizationStatus.MATCHED, skill.getId(), skill.getName(), original);
        }
        return new ResolvedSkill(key, NormalizationStatus.NEW, null, original, original);
    }
}
