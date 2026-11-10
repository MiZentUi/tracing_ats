package ats.resume.domain.privacy;

/** Ошибка маскировки. Текст резюме в сообщение не попадает. */
public class PiiMaskingException extends RuntimeException {

    public PiiMaskingException(String message) {
        super(message);
    }

    public PiiMaskingException(String message, Throwable cause) {
        super(message, cause);
    }
}
