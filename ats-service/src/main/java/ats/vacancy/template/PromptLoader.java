package ats.vacancy.template;

import ats.vacancy.config.VacancyLlmProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class PromptLoader {

    private final String systemPrompt;
    private final String jsonSchema;

    public PromptLoader(ResourceLoader resourceLoader, VacancyLlmProperties properties) {
        String version = properties.getPromptVersion();
        this.systemPrompt = read(resourceLoader, "classpath:prompts/" + version + ".txt", "prompt");
        this.jsonSchema =
                read(resourceLoader, "classpath:prompts/" + version + ".schema.json", "schema");
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String jsonSchema() {
        return jsonSchema;
    }

    private static String read(ResourceLoader loader, String location, String kind) {
        Resource resource = loader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("LLM " + kind + " not found: " + location);
        }
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read LLM " + kind + ": " + location, e);
        }
    }
}
