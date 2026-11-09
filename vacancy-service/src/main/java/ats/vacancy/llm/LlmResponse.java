package ats.vacancy.llm;

/** Ответ LLM. Модель берётся из ответа провайдера, а не из конфигурации. */
public record LlmResponse(
        String content,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        String finishReason) {}
