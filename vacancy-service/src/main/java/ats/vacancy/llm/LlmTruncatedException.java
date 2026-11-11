package ats.vacancy.llm;

public class LlmTruncatedException extends LlmException {

    public LlmTruncatedException(String message) {
        super("LLM_INVALID_RESPONSE", 502, message, null);
    }
}
