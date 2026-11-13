package ats.vacancy.dictionary.generation;

import ats.vacancy.application.ApiException;
import ats.vacancy.config.VacancyLlmProperties;
import ats.vacancy.dictionary.DictionaryClient;
import ats.vacancy.dictionary.DictionaryModels;
import ats.vacancy.dictionary.DictionaryRpcException;
import ats.vacancy.dictionary.generation.SkillGenerationModels.AcceptedSkill;
import ats.vacancy.dictionary.generation.SkillGenerationModels.AliasInput;
import ats.vacancy.dictionary.generation.SkillGenerationModels.GenerationJob;
import ats.vacancy.dictionary.generation.SkillGenerationModels.SkillInput;
import ats.vacancy.llm.LlmClient;
import ats.vacancy.llm.LlmInvalidResponseException;
import ats.vacancy.llm.LlmRequest;
import ats.vacancy.llm.LlmResponse;
import ats.vacancy.template.TemplateParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Генерация навыков по специальности: LLM в ats, staging и проверка в dictionary. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillGenerationService {

    private static final String ONLY_JSON =
            "Верни только один JSON-объект по схеме, без markdown и текста вокруг.";
    private static final Set<String> STOP_WORDS =
            Set.of(
                    "специалист",
                    "разработчик",
                    "инженер",
                    "опыт",
                    "знание",
                    "умение",
                    "работа",
                    "профессия",
                    "квалификация");

    private final LlmClient llmClient;
    private final SkillGenerationPrompt prompt;
    private final SkillGenerationParser parser;
    private final SkillGenerationStore store;
    private final DictionaryClient dictionaryClient;
    private final VacancyLlmProperties llmProperties;

    public GenerationJob generate(String specialization, String note, Integer maxSkills) {
        String safeSpecialization = required(specialization, "specialization", 256);
        String safeNote = trim(note, 2000);
        int limit = clamp(maxSkills == null ? 30 : maxSkills, 1, 50);
        UUID jobId =
                store.createJob(
                        safeSpecialization,
                        safeNote,
                        limit,
                        "hr",
                        llmProperties.getModel(),
                        SkillGenerationPrompt.PROMPT_VERSION);
        long startedAt = System.nanoTime();
        log.info(
                "skill_generation_started job_id={} specialization_chars={} max_skills={}",
                jobId,
                safeSpecialization.length(),
                limit);
        try {
            store.markRunning(jobId);
            LlmRequest request =
                    new LlmRequest(
                            prompt.systemPrompt(),
                            prompt.userPrompt(safeSpecialization, safeNote),
                            prompt.jsonSchema());
            LlmResponse response = llmClient.complete(request);
            SkillGenerationPayload payload = parseWithRetry(request, response);
            List<SkillInput> drafts = buildDrafts(payload, limit, safeSpecialization);
            if (drafts.isEmpty()) {
                throw new TemplateParseException("LLM returned no usable skills");
            }
            store.saveDrafts(jobId, drafts, response.content());
            GenerationJob job =
                    store.find(jobId)
                            .orElseThrow(
                                    () -> new IllegalStateException("generation job disappeared"));
            log.info(
                    "skill_generation_finished job_id={} skills={} aliases={} elapsed_ms={}",
                    jobId,
                    drafts.size(),
                    drafts.stream().mapToInt(item -> item.aliases().size()).sum(),
                    elapsedMs(startedAt));
            return job;
        } catch (RuntimeException exception) {
            store.markFailed(jobId, safeError(exception));
            log.warn(
                    "skill_generation_failed job_id={} error={} elapsed_ms={}",
                    jobId,
                    exception.getClass().getSimpleName(),
                    elapsedMs(startedAt));
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            if (exception instanceof LlmInvalidResponseException) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "LLM_INVALID_RESPONSE",
                        "LLM returned an invalid skill generation response");
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "SKILL_GENERATION_FAILED", "Skill generation failed");
        }
    }

    public GenerationJob get(UUID jobId) {
        return store.find(jobId)
                .orElseThrow(() -> ApiException.notFound("Generation job not found"));
    }

    public List<GenerationJob> list(int limit) {
        return store.list(limit);
    }

    public GenerationJob approve(UUID jobId, List<AcceptedSkill> accepted) {
        GenerationJob job = get(jobId);
        if (!"DONE".equals(job.status())) {
            throw ApiException.conflict(
                    "GENERATION_NOT_READY", "Generation job is not ready for approval");
        }
        List<AcceptedSkill> selected =
                accepted == null || accepted.isEmpty() ? allAccepted(job) : accepted;
        if (selected.isEmpty()) {
            throw ApiException.unprocessable("NOTHING_SELECTED", "No skills selected for approval");
        }
        List<DictionaryModels.ApplySkill> apply = new ArrayList<>();
        List<UUID> skillIds = new ArrayList<>();
        List<UUID> aliasIds = new ArrayList<>();
        for (AcceptedSkill acceptedSkill : selected) {
            var draft =
                    job.skills().stream()
                            .filter(item -> item.id().equals(acceptedSkill.draftId()))
                            .findFirst()
                            .orElseThrow(() -> ApiException.notFound("Generation draft not found"));
            if (draft.conflictSkillId() != null) {
                throw ApiException.conflict(
                        "SKILL_CONFLICT", "Draft conflicts with existing skill");
            }
            String canonical =
                    acceptedSkill.canonical() == null || acceptedSkill.canonical().isBlank()
                            ? draft.canonical()
                            : acceptedSkill.canonical().strip();
            String skillType =
                    acceptedSkill.skillType() == null || acceptedSkill.skillType().isBlank()
                            ? draft.skillType()
                            : acceptedSkill.skillType();
            UUID target =
                    acceptedSkill.targetSkillId() == null
                            ? draft.targetSkillId()
                            : acceptedSkill.targetSkillId();
            List<String> requestedAliases = acceptedSkill.aliases();
            List<DictionaryModels.ApplyAlias> aliases = new ArrayList<>();
            for (var alias : draft.aliases()) {
                if (alias.conflictSkillId() != null) {
                    continue;
                }
                boolean acceptedAlias =
                        requestedAliases == null
                                || requestedAliases.stream()
                                        .anyMatch(
                                                value ->
                                                        value != null
                                                                && value.equalsIgnoreCase(
                                                                        alias.text()));
                if (!acceptedAlias) {
                    continue;
                }
                aliases.add(
                        new DictionaryModels.ApplyAlias(
                                alias.text(), alias.lang() == null ? "mixed" : alias.lang()));
                aliasIds.add(alias.id());
            }
            apply.add(
                    new DictionaryModels.ApplySkill(
                            canonical,
                            skillType,
                            target == null ? null : target.toString(),
                            aliases));
            skillIds.add(draft.id());
        }
        try {
            DictionaryModels.Applied applied = dictionaryClient.applySkills(apply);
            store.markApproved(jobId, skillIds, aliasIds);
            log.info(
                    "skill_generation_approved job_id={} skills={} resolver_version={}",
                    jobId,
                    applied.skills().size(),
                    applied.resolverVersion());
            return get(jobId);
        } catch (DictionaryRpcException exception) {
            throw ApiException.conflict("DICTIONARY_CONFLICT", exception.getMessage());
        }
    }

    public GenerationJob reject(UUID jobId) {
        get(jobId);
        store.markRejected(jobId);
        log.info("skill_generation_rejected job_id={}", jobId);
        return get(jobId);
    }

    private SkillGenerationPayload parseWithRetry(LlmRequest request, LlmResponse response) {
        try {
            return parser.parse(response.content());
        } catch (TemplateParseException first) {
            log.warn("skill_generation_invalid_response attempt=1 reason={}", first.getMessage());
            LlmResponse retry =
                    llmClient.complete(
                            new LlmRequest(
                                    request.systemPrompt(),
                                    request.userPrompt() + "\n\n" + ONLY_JSON,
                                    request.jsonSchema()));
            try {
                return parser.parse(retry.content());
            } catch (TemplateParseException second) {
                throw new LlmInvalidResponseException(
                        "LLM returned an invalid skill generation response after retry", second);
            }
        }
    }

    private List<SkillInput> buildDrafts(
            SkillGenerationPayload payload, int maxSkills, String specialization) {
        List<SkillInput> result = new ArrayList<>();
        Set<String> canonicalSeen = new HashSet<>();
        for (SkillGenerationPayload.SkillDraft draft : payload.skills()) {
            if (result.size() >= maxSkills) {
                break;
            }
            if (draft == null) {
                continue;
            }
            String canonical = trim(draft.canonical(), 256);
            if (canonical == null || STOP_WORDS.contains(canonical.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!canonicalSeen.add(canonical.toLowerCase(Locale.ROOT))) {
                continue;
            }
            DictionaryModels.Resolved target = dictionaryClient.resolve(canonical, "ADMIN", true);
            UUID targetId = target.skillId();
            String targetName = target.skillId() == null ? null : target.canonicalName();
            List<AliasInput> aliases = new ArrayList<>();
            Set<String> aliasSeen = new HashSet<>();
            for (SkillGenerationPayload.AliasDraft aliasDraft :
                    draft.aliases() == null
                            ? List.<SkillGenerationPayload.AliasDraft>of()
                            : draft.aliases()) {
                if (aliasDraft == null) {
                    continue;
                }
                String text = trim(aliasDraft.text(), 256);
                if (text == null
                        || text.equalsIgnoreCase(canonical)
                        || !aliasSeen.add(text.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                DictionaryModels.Resolved aliasResolved =
                        dictionaryClient.resolve(text, "ADMIN", true);
                UUID conflictId = null;
                String conflictName = null;
                if (aliasResolved.skillId() != null) {
                    if (targetId != null && aliasResolved.skillId().equals(targetId)) {
                        continue;
                    }
                    conflictId = aliasResolved.skillId();
                    conflictName = aliasResolved.canonicalName();
                }
                aliases.add(
                        new AliasInput(
                                text,
                                aliasDraft.lang() == null ? "mixed" : aliasDraft.lang(),
                                aliasDraft.category(),
                                conflictId,
                                conflictName));
            }
            result.add(
                    new SkillInput(
                            canonical,
                            SkillGenerationParser.skillType(draft.skillType()),
                            targetId,
                            targetName,
                            null,
                            null,
                            aliases));
        }
        log.info(
                "skill_generation_drafts specialization_chars={} skills={} aliases={}",
                specialization.length(),
                result.size(),
                result.stream().mapToInt(item -> item.aliases().size()).sum());
        return result;
    }

    private static List<AcceptedSkill> allAccepted(GenerationJob job) {
        return job.skills().stream()
                .filter(draft -> draft.conflictSkillId() == null)
                .map(
                        draft ->
                                new AcceptedSkill(
                                        draft.id(),
                                        draft.canonical(),
                                        draft.skillType(),
                                        draft.targetSkillId(),
                                        draft.aliases().stream()
                                                .filter(alias -> alias.conflictSkillId() == null)
                                                .map(SkillGenerationModels.AliasDraft::text)
                                                .toList()))
                .toList();
    }

    private static String required(String value, String field, int maxLength) {
        String result = trim(value, maxLength);
        if (result == null) {
            throw ApiException.unprocessable("INVALID_" + field.toUpperCase(Locale.ROOT), field);
        }
        return result;
    }

    private static String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.strip();
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private static String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(500, message.length()));
    }
}
