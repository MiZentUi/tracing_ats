package ats.resume.infrastructure.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Разбирает строгий JSON-ответ LLM и проверяет длину темы и тела письма. */
@Component
public class CandidateMessageParser {

    private static final int SUBJECT_MIN = 3;
    private static final int SUBJECT_MAX = 200;
    private static final int BODY_MIN = 80;
    private static final int BODY_MAX = 3000;

    private final ObjectMapper objectMapper;

    public CandidateMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedMessage parse(String content) {
        if (content == null || content.isBlank()) {
            throw new CandidateMessageParseException("LLM response is empty");
        }
        String normalized = stripFence(content.strip());
        if (normalized.isBlank()) {
            throw new CandidateMessageParseException("LLM response is empty after fence removal");
        }

        Payload payload;
        try {
            payload = objectMapper.readValue(normalized, Payload.class);
        } catch (JsonProcessingException e) {
            throw new CandidateMessageParseException("LLM response is not valid JSON", e);
        }

        String subject = payload.subject() == null ? "" : payload.subject().strip();
        String body = payload.body() == null ? "" : payload.body().strip();
        if (subject.length() < SUBJECT_MIN || subject.length() > SUBJECT_MAX) {
            throw new CandidateMessageParseException("subject length is out of range");
        }
        if (body.length() < BODY_MIN || body.length() > BODY_MAX) {
            throw new CandidateMessageParseException("body length is out of range");
        }
        return new ParsedMessage(subject, body);
    }

    /** Единственная разрешённая чистка — снятие markdown-ограждения. */
    static String stripFence(String value) {
        String result = value;
        if (result.startsWith("```json")) {
            result = result.substring("```json".length());
        } else if (result.startsWith("```")) {
            result = result.substring("```".length());
        } else {
            return result;
        }
        int closing = result.lastIndexOf("```");
        if (closing >= 0) {
            result = result.substring(0, closing);
        }
        return result.strip();
    }

    public record ParsedMessage(String subject, String body) {}

    private record Payload(String subject, String body) {}
}
