package ats.extraction.domain.error;

import java.util.Objects;

public class ExtractionException extends RuntimeException {

    private final ErrorCode errorCode;

    public ExtractionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ExtractionException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
