package ats.vacancy.template;

import ats.vacancy.config.VacancyLlmProperties;
import ats.vacancy.llm.LlmClient;
import ats.vacancy.llm.LlmInvalidResponseException;
import ats.vacancy.llm.LlmRequest;
import ats.vacancy.llm.LlmResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Один логический разбор: промпт, вызов LLM, один повтор при невалидном ответе, очистка. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateExtractionService {

    private static final Pattern CLOSING_TAG =
            Pattern.compile("<\\s*/\\s*vacancy_text\\s*>", Pattern.CASE_INSENSITIVE);
    private static final String ONLY_JSON_INSTRUCTION =
            "Верни только один JSON-объект по схеме, без текста вокруг.";

    private final LlmClient llmClient;
    private final PromptLoader promptLoader;
    private final TemplateResponseParser responseParser;
    private final RequirementCleaner cleaner;
    private final KindHistoryService historyService;
    private final VacancyLlmProperties properties;

    public ExtractedTemplate extract(
            String vacancyText, UUID specializationId, UUID levelId, String modelHint) {
        long startedAt = System.nanoTime();
        log.info(
                "template_extraction_started text_chars={} specialization_id={} level_id={}"
                        + " model={} prompt_version={}",
                vacancyText.length(),
                specializationId,
                levelId,
                modelHint,
                properties.getPromptVersion());

        LlmRequest request =
                new LlmRequest(
                        promptLoader.systemPrompt(),
                        userPrompt(vacancyText),
                        promptLoader.jsonSchema());

        LlmResponse response = llmClient.complete(request);
        log.info(
                "template_response_received model={} content_chars={} finish_reason={}"
                        + " elapsed_ms={}",
                response.model(),
                response.content() == null ? 0 : response.content().length(),
                response.finishReason(),
                elapsedMs(startedAt));

        LlmTemplatePayload payload;
        try {
            payload = responseParser.parse(response.content());
        } catch (TemplateParseException firstFailure) {
            log.warn(
                    "template_response_invalid attempt=1 reason={} content_chars={}",
                    firstFailure.getMessage(),
                    response.content() == null ? 0 : response.content().length());
            LlmRequest retry =
                    new LlmRequest(
                            request.systemPrompt(),
                            request.userPrompt() + "\n\n" + ONLY_JSON_INSTRUCTION,
                            request.jsonSchema());
            LlmResponse retryResponse = llmClient.complete(retry);
            try {
                payload = responseParser.parse(retryResponse.content());
                response = retryResponse;
            } catch (TemplateParseException secondFailure) {
                log.error(
                        "template_response_invalid attempt=2 reason={} first_reason={}"
                                + " content_chars={}",
                        secondFailure.getMessage(),
                        firstFailure.getMessage(),
                        retryResponse.content() == null ? 0 : retryResponse.content().length());
                throw new LlmInvalidResponseException(
                        "LLM returned an invalid template response after retry", secondFailure);
            }
        }

        CleanedTemplate cleaned = cleaner.clean(payload, vacancyText);
        var withHistory = historyService.apply(cleaned.requirements(), specializationId, levelId);
        log.info(
                "template_extraction_finished model={} requirements={} dropped={}"
                        + " title_present={} elapsed_ms={}",
                response.model() == null || response.model().isBlank()
                        ? modelHint
                        : response.model(),
                withHistory.size(),
                cleaned.droppedRequirements(),
                cleaned.title() == null ? "missing" : "present",
                elapsedMs(startedAt));
        return new ExtractedTemplate(
                cleaned.title(),
                withHistory,
                response.model() == null || response.model().isBlank()
                        ? modelHint
                        : response.model(),
                properties.getPromptVersion(),
                cleaned.droppedRequirements());
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    static String userPrompt(String vacancyText) {
        String safe = CLOSING_TAG.matcher(vacancyText).replaceAll("</vacancy_text_>");
        return "<vacancy_text>\n" + safe + "\n</vacancy_text>";
    }
}
