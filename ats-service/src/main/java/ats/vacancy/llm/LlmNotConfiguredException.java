package ats.vacancy.llm;

public class LlmNotConfiguredException extends LlmException {

    public LlmNotConfiguredException() {
        super(
                "LLM_NOT_CONFIGURED",
                503,
                "LLM API key is not configured. Set OPENROUTER_API_KEY or CLOUDFLARE_AI_API_TOKEN"
                        + " (with CLOUDFLARE_AI_ENABLED=true) or use the mock-llm profile.",
                null);
    }
}
