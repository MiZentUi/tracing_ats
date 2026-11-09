package ats.vacancy.template;

import ats.vacancy.infrastructure.persistence.SkillAliasRepository;
import ats.vacancy.infrastructure.persistence.SkillEntity;
import ats.vacancy.infrastructure.persistence.SkillRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Словарь синонимов: только точное совпадение нормализованного ключа, без LLM. */
@Component
@RequiredArgsConstructor
public class SkillCatalog {

    private final SkillAliasRepository aliasRepository;
    private final SkillRepository skillRepository;

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
