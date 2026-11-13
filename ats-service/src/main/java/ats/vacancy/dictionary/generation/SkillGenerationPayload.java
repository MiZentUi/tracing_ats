package ats.vacancy.dictionary.generation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Строгая схема ответа LLM для генерации навыков по специальности. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillGenerationPayload(
        @JsonProperty("schema_version") Integer schemaVersion,
        String specialization,
        List<SkillDraft> skills) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillDraft(
            String canonical,
            @JsonProperty("skill_type") String skillType,
            List<AliasDraft> aliases) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AliasDraft(String text, String lang, String category) {}
}
