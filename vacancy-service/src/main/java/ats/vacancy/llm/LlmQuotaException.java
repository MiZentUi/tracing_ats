package ats.vacancy.llm;

public class LlmQuotaException extends LlmException {

    public LlmQuotaException(String message, Throwable cause) {
        super("LLM_UNAVAILABLE", 503, message, cause);
    }
}
