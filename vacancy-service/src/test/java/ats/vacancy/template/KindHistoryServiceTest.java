package ats.vacancy.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ats.vacancy.config.TemplateProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class KindHistoryServiceTest {

    private final KindHistoryService service =
            new KindHistoryService(mock(NamedParameterJdbcTemplate.class), properties());

    private static TemplateProperties properties() {
        TemplateProperties properties = new TemplateProperties();
        properties.getKindHistory().setMinObservations(3);
        properties.getKindHistory().setMinShare(0.6);
        return properties;
    }

    @Test
    void appliesRfcTable() {
        assertThat(service.decide(Kind.NICE, new SkillKindHistory(0, 0, 0)).source())
                .isEqualTo(KindSource.LLM);
        assertThat(service.decide(Kind.NICE, new SkillKindHistory(1, 0, 0)).source())
                .isEqualTo(KindSource.LLM);
        assertThat(service.decide(Kind.NICE, new SkillKindHistory(4, 1, 0)))
                .extracting(
                        HistoryDecision::kind, HistoryDecision::source, HistoryDecision::llmKind)
                .containsExactly(Kind.MUST, KindSource.HISTORY, Kind.NICE);
        assertThat(service.decide(Kind.OPTIONAL, new SkillKindHistory(2, 2, 0)).source())
                .isEqualTo(KindSource.LLM);
        assertThat(service.decide(Kind.MUST, new SkillKindHistory(3, 2, 1)).source())
                .isEqualTo(KindSource.LLM);
    }

    @Test
    void doesNotApplyHintBelowMinObservations() {
        assertThat(service.decide(Kind.MUST, new SkillKindHistory(2, 0, 0)).source())
                .isEqualTo(KindSource.LLM);
    }
}
