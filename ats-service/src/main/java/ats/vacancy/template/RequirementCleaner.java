package ats.vacancy.template;

import ats.vacancy.config.VacancyLlmProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Детерминированная очистка и дедупликация требований после разбора ответа LLM. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequirementCleaner {

    private final SkillCatalog skillCatalog;
    private final VacancyLlmProperties llmProperties;

    public CleanedTemplate clean(LlmTemplatePayload payload, String vacancyText) {
        Map<String, SkillRef> aliases = skillCatalog.loadAliases();
        Map<String, DraftRequirement> byKey = new LinkedHashMap<>();
        int dropped = 0;

        for (LlmTemplatePayload.LlmRequirement raw : payload.requirements()) {
            String quote = raw.quote() == null ? "" : raw.quote().strip();
            if (!QuoteMatcher.isSubstring(quote, vacancyText)) {
                dropped++;
                log.debug("Requirement dropped: quote is not a substring");
                continue;
            }

            String skill = raw.skill() == null ? null : raw.skill().strip();
            if (skill != null && skill.length() > 128) {
                skill = null;
            }
            String clarity = raw.clarityWarning() == null ? null : raw.clarityWarning().strip();
            if (clarity != null && clarity.isEmpty()) {
                clarity = null;
            }
            if (skill == null && clarity == null) {
                dropped++;
                continue;
            }

            BigDecimal years = normalizeYears(raw.yearsRequired());
            Kind kind = TemplateResponseParser.parseKind(raw.kind());
            ResolvedSkill resolved = skillCatalog.resolve(skill, aliases);

            DraftRequirement draft =
                    new DraftRequirement(
                            resolved.name(),
                            resolved.skillId(),
                            resolved.status(),
                            kind,
                            KindSource.LLM,
                            kind,
                            years,
                            quote.isEmpty() ? null : quote,
                            clarity,
                            null,
                            resolved.normalizedKey());

            String duplicateKey = duplicateKey(draft);
            DraftRequirement existing = byKey.get(duplicateKey);
            if (existing == null) {
                byKey.put(duplicateKey, draft);
            } else {
                dropped++;
                if (priority(draft.kind()) > priority(existing.kind())) {
                    // Цитата и срок остаются у первого вхождения, тип берём сильнейший.
                    byKey.put(
                            duplicateKey,
                            existing.withKindAndHistory(
                                    draft.kind(), KindSource.LLM, draft.llmKind(), null));
                }
            }
        }

        List<DraftRequirement> result = new ArrayList<>(byKey.values());
        int max = llmProperties.getMaxRequirements();
        if (result.size() > max) {
            dropped += result.size() - max;
            result = new ArrayList<>(result.subList(0, max));
        }
        String title = payload.title() == null ? null : payload.title().strip();
        if (title != null && title.isEmpty()) {
            title = null;
        }
        return new CleanedTemplate(title, List.copyOf(result), dropped);
    }

    private static BigDecimal normalizeYears(BigDecimal years) {
        if (years == null) {
            return BigDecimal.ZERO;
        }
        if (years.compareTo(BigDecimal.ZERO) < 0 || years.compareTo(BigDecimal.valueOf(50)) > 0) {
            return BigDecimal.ZERO;
        }
        return years;
    }

    private static String duplicateKey(DraftRequirement draft) {
        if (draft.skillId() != null) {
            return "S:" + draft.skillId();
        }
        if (draft.skill() != null && !draft.skill().isBlank()) {
            return "N:" + draft.normalizedKey();
        }
        return "Q:" + QuoteMatcher.normalizeWhitespace(draft.quote());
    }

    private static int priority(Kind kind) {
        return switch (kind) {
            case MUST -> 3;
            case NICE -> 2;
            case OPTIONAL -> 1;
        };
    }
}
