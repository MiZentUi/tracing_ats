package ats.vacancy.llm;

public class LlmAuthException extends LlmException {

    public LlmAuthException(String message, Throwable cause) {
        super("LLM_UNAVAILABLE", 503, message, cause);
    }
}
