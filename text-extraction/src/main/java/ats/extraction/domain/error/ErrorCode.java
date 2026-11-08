package ats.extraction.domain.error;

public enum ErrorCode {
    FILE_TOO_LARGE(413),
    UNSUPPORTED_TYPE(415),
    CORRUPTED_FILE(422),
    ENCRYPTED_FILE(422),
    TEXT_EMPTY(422),
    LIMIT_EXCEEDED(422),
    EXTRACTION_TIMEOUT(504);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
