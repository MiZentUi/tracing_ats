package ats.vacancy.llm;

/** Запрос к LLM: системная и пользовательская части плюс необязательная JSON-схема ответа. */
public record LlmRequest(String systemPrompt, String userPrompt, String jsonSchema) {}
