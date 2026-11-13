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
        int droppedAmbiguous = 0;
        int droppedLabel = 0;
        int droppedDuplicate = 0;
        int droppedLimit = 0;
        List<String> dropExamples = new ArrayList<>();

        for (LlmTemplatePayload.LlmRequirement raw : payload.requirements()) {
            String quote = raw.quote() == null ? "" : raw.quote().strip();
            if (!QuoteMatcher.isSubstring(quote, vacancyText)) {
                dropped++;
                droppedQuote++;
                log.debug(
                        "template_requirement_dropped reason=quote_mismatch qualification={}",
                        raw.qualification() != null);
                addExample(
                        dropExamples,
                        "quote_mismatch",
                        raw.qualification() != null ? "qualification" : raw.skill());
                continue;
            }

            String skill = stripToNull(raw.skill());
            if (skill != null && skill.length() > 128) {
                skill = null;
            }
            String qualification = stripToNull(raw.qualification());
            if (qualification != null && qualification.length() > 128) {
                qualification = null;
            }
            if (skill != null && qualification != null) {
                // Одно требование — один субъект. Смешанный ответ неоднозначен.
                dropped++;
                droppedAmbiguous++;
                log.debug("template_requirement_dropped reason=ambiguous_subject");
                addExample(dropExamples, "ambiguous_subject", "qualification");
                continue;
            }
            String clarity = stripToNull(raw.clarityWarning());
            String subject = qualification != null ? qualification : skill;
            if (subject == null && clarity == null) {
                dropped++;
                droppedNoSubject++;
                log.debug(
                        "template_requirement_dropped reason=no_subject qualification={}",
                        qualification != null);
                addExample(
                        dropExamples, "no_subject", qualification != null ? "qualification" : null);
                continue;
            }
            if (qualification != null && !QuoteMatcher.isSubstring(qualification, quote)) {
                // label — дословный фрагмент цитаты, иначе это уже не доказательство.
                dropped++;
                droppedLabel++;
                log.debug("template_requirement_dropped reason=label_mismatch");
                addExample(dropExamples, "label_mismatch", "qualification");
                continue;
            }

            BigDecimal years = normalizeYears(raw.yearsRequired());
            Kind kind = TemplateResponseParser.parseKind(raw.kind());
            ResolvedSkill resolved;
            boolean isQualification = qualification != null;
            if (isQualification) {
                resolved =
                        new ResolvedSkill(
                                SkillNormalizer.normalizeKey(qualification),
                                NormalizationStatus.NEW,
                                null,
                                qualification,
                                qualification);
            } else {
                resolved = skillCatalog.resolve(skill, aliases);
            }

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
                            resolved.normalizedKey(),
                            isQualification);

            String duplicateKey = duplicateKey(draft);
            DraftRequirement existing = byKey.get(duplicateKey);
            if (existing == null) {
                byKey.put(duplicateKey, draft);
            } else {
                dropped++;
                droppedDuplicate++;
                log.debug(
                        "template_requirement_dropped reason=duplicate qualification={}",
                        draft.qualification());
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
                            + " no_subject={} ambiguous={} label_mismatch={}"
                            + " duplicates={} limit={} examples={}",
                    payload.requirements().size(),
                    droppedQuote,
                    droppedNoSubject,
                    droppedAmbiguous,
                    droppedLabel,
                    droppedDuplicate,
                    droppedLimit,
                    dropExamples);
        }
        return new CleanedTemplate(title, List.copyOf(result), dropped);
    }

    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
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
        if (draft.qualification()) {
            return "QUAL:" + draft.normalizedKey();
        }
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
