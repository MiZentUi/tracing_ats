package ats.vacancy.llm;

public class LlmInvalidResponseException extends LlmException {

    public LlmInvalidResponseException(String message, Throwable cause) {
        super("LLM_INVALID_RESPONSE", 502, message, cause);
    }
}
