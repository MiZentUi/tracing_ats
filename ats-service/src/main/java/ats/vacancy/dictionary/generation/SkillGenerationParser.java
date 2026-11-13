package ats.vacancy.dictionary.generation;

import ats.vacancy.template.TemplateParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Парсер ответа LLM: только снятие markdown-ограждения и проверка схемы. */
@Component
public class SkillGenerationParser {

    public static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public SkillGenerationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SkillGenerationPayload parse(String content) {
        if (content == null || content.isBlank()) {
            throw new TemplateParseException("LLM response is empty");
        }
        String normalized = stripFence(content.strip());
        SkillGenerationPayload payload;
        try {
            payload = objectMapper.readValue(normalized, SkillGenerationPayload.class);
        } catch (JsonProcessingException exception) {
            throw new TemplateParseException("LLM response is not valid JSON", exception);
        }
        if (payload.schemaVersion() == null || payload.schemaVersion() != SCHEMA_VERSION) {
            throw new TemplateParseException(
                    "Unsupported schema_version: " + payload.schemaVersion());
        }
        if (payload.skills() == null) {
            throw new TemplateParseException("skills is missing");
        }
        return payload;
    }

    static String skillType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "OTHER";
        }
        return switch (raw.strip().toUpperCase(Locale.ROOT)) {
            case "TECH" -> "TECH";
            case "TOOL" -> "TOOL";
            case "DOMAIN" -> "DOMAIN";
            case "SOFT" -> "SOFT";
            default -> "OTHER";
        };
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
