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
        int droppedQuote = 0;
        int droppedNoSubject = 0;
        int droppedDuplicate = 0;
        int droppedLimit = 0;
        List<String> dropExamples = new ArrayList<>();

        for (LlmTemplatePayload.LlmRequirement raw : payload.requirements()) {
            String quote = raw.quote() == null ? "" : raw.quote().strip();
            if (!QuoteMatcher.isSubstring(quote, vacancyText)) {
                dropped++;
                droppedQuote++;
                log.debug(
                        "template_requirement_dropped reason=quote_mismatch skill={}", raw.skill());
                addExample(dropExamples, "quote_mismatch", raw.skill());
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
                droppedNoSubject++;
                log.debug("template_requirement_dropped reason=no_subject skill={}", skill);
                addExample(dropExamples, "no_subject", skill);
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
                droppedDuplicate++;
                log.debug("template_requirement_dropped reason=duplicate skill={}", draft.skill());
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
            droppedLimit = result.size() - max;
            dropped += droppedLimit;
            result = new ArrayList<>(result.subList(0, max));
        }
        String title = payload.title() == null ? null : payload.title().strip();
        if (title != null && title.isEmpty()) {
            title = null;
        }
        if (result.isEmpty() && !payload.requirements().isEmpty()) {
            log.warn(
                    "template_requirements_all_dropped total={} quote_mismatch={}"
                            + " no_subject={} duplicates={} limit={} examples={}",
                    payload.requirements().size(),
                    droppedQuote,
                    droppedNoSubject,
                    droppedDuplicate,
                    droppedLimit,
                    dropExamples);
        }
        return new CleanedTemplate(title, List.copyOf(result), dropped);
    }

    private static void addExample(List<String> examples, String reason, String skill) {
        if (examples.size() >= 3) {
            return;
        }
        examples.add(reason + " skill=" + skill);
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
