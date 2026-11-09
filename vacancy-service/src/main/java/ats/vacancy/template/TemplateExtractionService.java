package ats.vacancy.template;

import ats.vacancy.config.VacancyLlmProperties;
import ats.vacancy.llm.LlmClient;
import ats.vacancy.llm.LlmInvalidResponseException;
import ats.vacancy.llm.LlmRequest;
import ats.vacancy.llm.LlmResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Один логический разбор: промпт, вызов LLM, один повтор при невалидном ответе, очистка. */
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
        LlmRequest request =
                new LlmRequest(
                        promptLoader.systemPrompt(),
                        userPrompt(vacancyText),
                        promptLoader.jsonSchema());

        LlmResponse response = llmClient.complete(request);
        LlmTemplatePayload payload;
        try {
            payload = responseParser.parse(response.content());
        } catch (TemplateParseException firstFailure) {
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
                throw new LlmInvalidResponseException(
                        "LLM returned an invalid template response after retry", secondFailure);
            }
        }

        CleanedTemplate cleaned = cleaner.clean(payload, vacancyText);
        var withHistory = historyService.apply(cleaned.requirements(), specializationId, levelId);
        return new ExtractedTemplate(
                cleaned.title(),
                withHistory,
                response.model() == null || response.model().isBlank()
                        ? modelHint
                        : response.model(),
                properties.getPromptVersion(),
                cleaned.droppedRequirements());
    }

    static String userPrompt(String vacancyText) {
        String safe = CLOSING_TAG.matcher(vacancyText).replaceAll("</vacancy_text_>");
        return "<vacancy_text>\n" + safe + "\n</vacancy_text>";
    }
}
