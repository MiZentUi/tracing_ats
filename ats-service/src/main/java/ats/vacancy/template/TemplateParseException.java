package ats.vacancy.template;

/** Внутренняя ошибка разбора: означает, что нужен один повтор, затем LlmInvalidResponse. */
public class TemplateParseException extends RuntimeException {

    public TemplateParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateParseException(String message) {
        super(message);
    }
}
