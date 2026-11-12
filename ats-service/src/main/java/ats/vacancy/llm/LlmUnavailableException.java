package ats.vacancy.llm;

public class LlmUnavailableException extends LlmException {

    public LlmUnavailableException(String message, Throwable cause) {
        super("LLM_UNAVAILABLE", 503, message, cause);
    }
}
