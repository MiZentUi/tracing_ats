package ats.resume.infrastructure.report;

/** Невалидный ответ LLM на запрос письма кандидату: нужен один повтор, затем ошибка. */
public class CandidateMessageParseException extends RuntimeException {

    public CandidateMessageParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public CandidateMessageParseException(String message) {
        super(message);
    }
}
