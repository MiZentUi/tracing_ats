package ats.resume.infrastructure.notification;

/** Ошибка отправки уведомления. retryable=false означает, что повтор не поможет. */
public class NotificationException extends RuntimeException {

    private final boolean retryable;

    public NotificationException(boolean retryable, String message) {
        super(message);
        this.retryable = retryable;
    }

    public NotificationException(boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
