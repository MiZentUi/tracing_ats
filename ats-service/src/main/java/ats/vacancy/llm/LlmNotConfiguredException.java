package ats.vacancy.llm;

public class LlmNotConfiguredException extends LlmException {

    public LlmNotConfiguredException() {
        super(
                "LLM_NOT_CONFIGURED",
                503,
                "LLM API key is not configured. Set OPENROUTER_API_KEY or use the mock-llm profile.",
                null);
    }
}
