package ats.resume.infrastructure.report;

import ats.gateway.model.CandidateMessageKind;
import ats.resume.infrastructure.json.JsonCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Загружает версионированные промпты писем и оборачивает данные в безопасный блок. */
@Component
public class CandidateMessagePrompt {

    static final String INVITATION_VERSION = "candidate-message.invitation.v1";
    static final String REJECTION_VERSION = "candidate-message.rejection.v1";

    private static final Pattern CLOSING_TAG =
            Pattern.compile("<\\s*/\\s*candidate_message_data\\s*>", Pattern.CASE_INSENSITIVE);

    private final Map<CandidateMessageKind, Prompt> prompts =
            new EnumMap<>(CandidateMessageKind.class);
    private final JsonCodec jsonCodec;

    public CandidateMessagePrompt(ResourceLoader resourceLoader, JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
        prompts.put(
                CandidateMessageKind.INTERVIEW_INVITATION,
                load(resourceLoader, INVITATION_VERSION));
        prompts.put(CandidateMessageKind.REJECTION, load(resourceLoader, REJECTION_VERSION));
    }

    public Prompt prompt(CandidateMessageKind kind) {
        Prompt prompt = prompts.get(kind);
        if (prompt == null) {
            throw new IllegalArgumentException("Unsupported candidate message kind: " + kind);
        }
        return prompt;
    }

    public String userPrompt(Object data) {
        String json = jsonCodec.write(data);
        String safe = CLOSING_TAG.matcher(json).replaceAll("</candidate_message_data_>");
        return "<candidate_message_data>\n" + safe + "\n</candidate_message_data>";
    }

    private static Prompt load(ResourceLoader loader, String version) {
        return new Prompt(
                version,
                read(loader, "classpath:prompts/" + version + ".txt", "prompt"),
                read(loader, "classpath:prompts/" + version + ".schema.json", "schema"));
    }

    private static String read(ResourceLoader loader, String location, String kind) {
        Resource resource = loader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Candidate message " + kind + " not found: " + location);
        }
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read candidate message " + kind, e);
        }
    }

    public record Prompt(String version, String systemPrompt, String jsonSchema) {}
}
