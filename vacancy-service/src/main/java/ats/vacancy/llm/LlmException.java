package ats.vacancy.llm;

/** Базовая ошибка LLM-модуля. Публичный код и HTTP-статус задаются наследниками. */
public abstract class LlmException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    protected LlmException(String errorCode, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
