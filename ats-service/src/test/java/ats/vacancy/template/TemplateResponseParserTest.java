package ats.vacancy.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TemplateResponseParserTest {

    private final TemplateResponseParser parser = new TemplateResponseParser(new ObjectMapper());

    private static String payload(String kind) {
        return """
                {"schema_version": 3, "title": "Backend", "requirements": [
                  {"skill": "Java", "kind": "%s", "years_required": 3,
                   "quote": "Java от 3 лет", "clarity_warning": null}
                ]}
                """
                .formatted(kind);
    }

    @Test
    void parsesLeadingNewlines() {
        LlmTemplatePayload payload = parser.parse("\n\n" + payload("MUST"));
        assertThat(payload.requirements()).hasSize(1);
        assertThat(payload.requirements().get(0).kind()).isEqualTo("MUST");
    }

    @Test
    void parsesFencedJson() {
        LlmTemplatePayload payload = parser.parse("```json\n" + payload("NICE") + "\n```");
        assertThat(payload.requirements().get(0).kind()).isEqualTo("NICE");
    }

    @Test
    void acceptsOptionalKind() {
        assertThat(parser.parse(payload("OPTIONAL")).requirements()).hasSize(1);
    }

    @Test
    void ignoresUnknownFields() {
        String withExtra =
                """
                {"schema_version": 3, "title": null, "requirements": [
                  {"skill": "Java", "kind": "MUST", "years_required": null,
                   "quote": "Java", "clarity_warning": null, "weight": 3, "critical": true}
                ]}
                """;
        assertThat(parser.parse(withExtra).requirements()).hasSize(1);
    }

    @Test
    void rejectsTruncatedAndWrongSchemaAndUnknownKind() {
        assertThatThrownBy(() -> parser.parse("{\"schema_version\": 3,"))
                .isInstanceOf(TemplateParseException.class);
        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        payload("MUST")
                                                .replace(
                                                        "\"schema_version\": 3",
                                                        "\"schema_version\": 1")))
                .isInstanceOf(TemplateParseException.class);
        assertThatThrownBy(() -> parser.parse(payload("MAYBE")))
                .isInstanceOf(TemplateParseException.class);
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> parser.parse("  \n ")).isInstanceOf(TemplateParseException.class);
    }
}
