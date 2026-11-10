package ats.resume.infrastructure.vacancy;

/** Ошибка вызова vacancy-service. Сообщение не содержит текстов резюме и PII. */
public class VacancyClientException extends RuntimeException {

    public VacancyClientException(String message) {
        super(message);
    }

    public VacancyClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
