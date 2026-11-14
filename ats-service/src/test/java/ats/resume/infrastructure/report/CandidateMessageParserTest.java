package ats.resume.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CandidateMessageParserTest {

    private final CandidateMessageParser parser = new CandidateMessageParser(new ObjectMapper());

    @Test
    void parsesPlainJsonAndStripsWhitespace() {
        String body = "Здравствуйте! Спасибо за отклик. ".repeat(5);

        CandidateMessageParser.ParsedMessage message =
                parser.parse(
                        """
                        {"subject": "  Приглашение  ", "body": "%s"}
                        """
                                .formatted(body));

        assertThat(message.subject()).isEqualTo("Приглашение");
        assertThat(message.body()).startsWith("Здравствуйте!");
    }

    @Test
    void stripsMarkdownFence() {
        String body = "Текст письма. ".repeat(10);

        CandidateMessageParser.ParsedMessage message =
                parser.parse("```json\n{\"subject\":\"Тема\",\"body\":\"" + body + "\"}\n```");

        assertThat(message.subject()).isEqualTo("Тема");
        assertThat(message.body()).isEqualTo(body.strip());
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("не json"))
                .isInstanceOf(CandidateMessageParseException.class);
    }

    @Test
    void rejectsTooShortBody() {
        assertThatThrownBy(() -> parser.parse("{\"subject\":\"Тема\",\"body\":\"коротко\"}"))
                .isInstanceOf(CandidateMessageParseException.class)
                .hasMessageContaining("body");
    }

    @Test
    void rejectsEmptyResponse() {
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(CandidateMessageParseException.class);
    }
}
