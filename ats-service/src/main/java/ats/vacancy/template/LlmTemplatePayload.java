package ats.vacancy.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmTemplatePayload(
        @JsonProperty("schema_version") Integer schemaVersion,
        String title,
        List<LlmRequirement> requirements) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LlmRequirement(
            String skill,
            String qualification,
            String kind,
            @JsonProperty("years_required") BigDecimal yearsRequired,
            String quote,
            @JsonProperty("clarity_warning") String clarityWarning) {}
}
