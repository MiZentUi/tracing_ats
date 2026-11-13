package ats.vacancy.hh;

import org.springframework.http.HttpStatus;

/** Ошибка hh.ru с кодом, который сохраняется в статусе публикации/события. */
public class HhApiException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final boolean retryable;

    public HhApiException(String errorCode, HttpStatus status, boolean retryable, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.retryable = retryable;
    }

    public HhApiException(
            String errorCode,
            HttpStatus status,
            boolean retryable,
            String message,
            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }
}
