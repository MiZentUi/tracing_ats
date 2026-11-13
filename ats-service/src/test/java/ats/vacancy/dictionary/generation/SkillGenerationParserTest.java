package ats.vacancy.dictionary.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ats.vacancy.template.TemplateParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SkillGenerationParserTest {

    private final SkillGenerationParser parser = new SkillGenerationParser(new ObjectMapper());

    @Test
    void parsesFencedJsonAndSkills() {
        String content =
                """
                ```json
                {"schema_version":1,"specialization":"электрик","skills":[
                  {"canonical":"Электромонтаж","skill_type":"domain",
                   "aliases":[{"text":"монтаж электрики","lang":"ru","category":"SYNONYM"}]}
                ]}
                ```
                """;
        SkillGenerationPayload payload = parser.parse(content);
        assertThat(payload.schemaVersion()).isEqualTo(1);
        assertThat(payload.skills()).hasSize(1);
        assertThat(payload.skills().getFirst().canonical()).isEqualTo("Электромонтаж");
        assertThat(SkillGenerationParser.skillType(payload.skills().getFirst().skillType()))
                .isEqualTo("DOMAIN");
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        assertThatThrownBy(
                        () ->
                                parser.parse(
                                        "{\"schema_version\":2,\"specialization\":\"x\",\"skills\":[]}"))
                .isInstanceOf(TemplateParseException.class);
    }
}
