package ats.vacancy.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Единственная разрешённая «чистка» ответа: strip и снятие markdown-ограждения. Всё остальное
 * считается невалидным ответом.
 */
@Component
public class TemplateResponseParser {

    public static final int SCHEMA_VERSION = 3;

    private final ObjectMapper objectMapper;

    public TemplateResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LlmTemplatePayload parse(String content) {
        if (content == null || content.isBlank()) {
            throw new TemplateParseException("LLM response is empty");
        }
        String normalized = stripFence(content.strip());
        if (normalized.isBlank()) {
            throw new TemplateParseException("LLM response is empty after fence removal");
        }

        LlmTemplatePayload payload;
        try {
            payload = objectMapper.readValue(normalized, LlmTemplatePayload.class);
        } catch (JsonProcessingException e) {
            throw new TemplateParseException("LLM response is not valid JSON", e);
        }

        if (payload.schemaVersion() == null || payload.schemaVersion() != SCHEMA_VERSION) {
            throw new TemplateParseException(
                    "Unsupported schema_version: " + payload.schemaVersion());
        }
        List<LlmTemplatePayload.LlmRequirement> requirements = payload.requirements();
        if (requirements == null) {
            throw new TemplateParseException("requirements is missing");
        }
        for (LlmTemplatePayload.LlmRequirement requirement : requirements) {
            if (requirement == null) {
                throw new TemplateParseException("requirements contains null");
            }
            if (parseKind(requirement.kind()) == null) {
                throw new TemplateParseException("Unknown kind: " + requirement.kind());
            }
        }
        return payload;
    }

    static Kind parseKind(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Kind.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

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
}
