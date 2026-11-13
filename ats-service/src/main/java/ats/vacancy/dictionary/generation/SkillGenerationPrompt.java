package ats.vacancy.dictionary.generation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Промпт и JSON-схема генерации навыков. */
@Component
public class SkillGenerationPrompt {

    public static final String PROMPT_VERSION = "skill-generation.v1";

    private static final Pattern CLOSING_SPECIALIZATION =
            Pattern.compile("<\\s*/\\s*specialization\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSING_NOTE =
            Pattern.compile("<\\s*/\\s*note\\s*>", Pattern.CASE_INSENSITIVE);

    private final String systemPrompt;
    private final String jsonSchema;

    public SkillGenerationPrompt(ResourceLoader resourceLoader) {
        this.systemPrompt = read(resourceLoader, "classpath:prompts/" + PROMPT_VERSION + ".txt");
        this.jsonSchema =
                read(resourceLoader, "classpath:prompts/" + PROMPT_VERSION + ".schema.json");
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String jsonSchema() {
        return jsonSchema;
    }

    public String userPrompt(String specialization, String note) {
        String safeSpecialization =
                CLOSING_SPECIALIZATION
                        .matcher(specialization == null ? "" : specialization)
                        .replaceAll("&lt;/specialization&gt;");
        String safeNote =
                CLOSING_NOTE.matcher(note == null ? "" : note).replaceAll("&lt;/note&gt;");
        return "<specialization>\n"
                + safeSpecialization
                + "\n</specialization>\n<note>\n"
                + safeNote
                + "\n</note>";
    }

    private static String read(ResourceLoader loader, String location) {
        Resource resource = loader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("LLM prompt not found: " + location);
        }
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read LLM prompt: " + location, exception);
        }
    }
}
