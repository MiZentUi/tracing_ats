package ats.vacancy.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ats.vacancy.config.VacancyLlmProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequirementCleanerTest {

    private static final UUID JAVASCRIPT_ID = UUID.randomUUID();

    private final SkillCatalog catalog = mock(SkillCatalog.class);
    private final VacancyLlmProperties properties = new VacancyLlmProperties();
    private RequirementCleaner cleaner;

    @BeforeEach
    void setUp() {
        when(catalog.loadAliases()).thenReturn(Map.of());
        when(catalog.resolve(any(), any()))
                .thenAnswer(
                        invocation -> {
                            String raw = invocation.getArgument(0);
                            if (raw == null) {
                                return new ResolvedSkill(
                                        "", NormalizationStatus.NEW, null, null, null);
                            }
                            String key = SkillNormalizer.normalizeKey(raw);
                            if (key.equals("js") || key.equals("javascript")) {
                                return new ResolvedSkill(
                                        key,
                                        NormalizationStatus.MATCHED,
                                        JAVASCRIPT_ID,
                                        "JavaScript",
                                        raw);
                            }
                            return new ResolvedSkill(key, NormalizationStatus.NEW, null, raw, raw);
                        });
        cleaner = new RequirementCleaner(catalog, properties);
    }

    private static LlmTemplatePayload.LlmRequirement requirement(
            String skill, String kind, BigDecimal years, String quote, String warning) {
        return new LlmTemplatePayload.LlmRequirement(skill, null, kind, years, quote, warning);
    }

    private static LlmTemplatePayload.LlmRequirement qualification(
            String label, String kind, String quote) {
        return new LlmTemplatePayload.LlmRequirement(null, label, kind, null, quote, null);
    }

    @Test
    void dropsQuoteThatIsNotInText() {
        CleanedTemplate result =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(
                                        requirement(
                                                "Java", "MUST", null, "нет такой цитаты", null))),
                        "Java и SQL");
        assertThat(result.requirements()).isEmpty();
        assertThat(result.droppedRequirements()).isEqualTo(1);
    }

    @Test
    void matchesQuoteAcrossWhitespace() {
        CleanedTemplate result =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(requirement("Java", "MUST", null, "Java и SQL", null))),
                        "Java\nи\u00A0SQL");
        assertThat(result.requirements()).hasSize(1);
    }

    @Test
    void dropsRequirementWithoutSkillAndWarning() {
        CleanedTemplate result =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3, null, List.of(requirement(null, "NICE", null, "Java", null))),
                        "Java");
        assertThat(result.requirements()).isEmpty();
        assertThat(result.droppedRequirements()).isEqualTo(1);
    }

    @Test
    void mergesDuplicatesKeepingStrongestKindAndFirstQuote() {
        CleanedTemplate result =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(
                                        requirement(
                                                "JS",
                                                "NICE",
                                                BigDecimal.valueOf(2),
                                                "JS два года",
                                                null),
                                        requirement(
                                                "JavaScript",
                                                "MUST",
                                                BigDecimal.valueOf(5),
                                                "JavaScript пять лет",
                                                null))),
                        "JS два года\nJavaScript пять лет");
        assertThat(result.requirements()).hasSize(1);
        DraftRequirement requirement = result.requirements().get(0);
        assertThat(requirement.kind()).isEqualTo(Kind.MUST);
        assertThat(requirement.quote()).isEqualTo("JS два года");
        assertThat(requirement.yearsRequired()).isEqualByComparingTo("2");
        assertThat(requirement.skillId()).isEqualTo(JAVASCRIPT_ID);
    }

    @Test
    void rejectsOutOfRangeYears() {
        CleanedTemplate negative =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(
                                        requirement(
                                                "Java",
                                                "MUST",
                                                BigDecimal.valueOf(-1),
                                                "Java",
                                                null))),
                        "Java");
        CleanedTemplate huge =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(
                                        requirement(
                                                "Java",
                                                "MUST",
                                                BigDecimal.valueOf(100),
                                                "Java",
                                                null))),
                        "Java");
        assertThat(negative.requirements().get(0).yearsRequired()).isEqualByComparingTo("0");
        assertThat(huge.requirements().get(0).yearsRequired()).isEqualByComparingTo("0");
    }

    @Test
    void keepsQualificationAsOpenLabelWithoutSkill() {
        CleanedTemplate result =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(
                                        qualification(
                                                "III группа по электробезопасности",
                                                "MUST",
                                                "Допуск: III группа по электробезопасности."))),
                        "Допуск: III группа по электробезопасности.");

        assertThat(result.requirements()).hasSize(1);
        DraftRequirement requirement = result.requirements().get(0);
        assertThat(requirement.qualification()).isTrue();
        assertThat(requirement.skill()).isEqualTo("III группа по электробезопасности");
        assertThat(requirement.skillId()).isNull();
        assertThat(requirement.normalizedKey()).isEqualTo("iii группа по электробезопасности");
    }

    @Test
    void dropsQualificationWhenLabelIsNotInQuote() {
        CleanedTemplate result =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(
                                        qualification(
                                                "IV группа по электробезопасности",
                                                "MUST",
                                                "Допуск: III группа по электробезопасности."))),
                        "Допуск: III группа по электробезопасности.");

        assertThat(result.requirements()).isEmpty();
        assertThat(result.droppedRequirements()).isEqualTo(1);
    }

    @Test
    void dropsAmbiguousSkillAndQualification() {
        CleanedTemplate result =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(
                                        new LlmTemplatePayload.LlmRequirement(
                                                "Python",
                                                "III группа по электробезопасности",
                                                "MUST",
                                                null,
                                                "Python, III группа по электробезопасности",
                                                null))),
                        "Python, III группа по электробезопасности");

        assertThat(result.requirements()).isEmpty();
        assertThat(result.droppedRequirements()).isEqualTo(1);
    }

    @Test
    void truncatesToMaxRequirements() {
        properties.setMaxRequirements(2);
        CleanedTemplate result =
                cleaner.clean(
                        new LlmTemplatePayload(
                                3,
                                null,
                                List.of(
                                        requirement("Java", "MUST", null, "Java", null),
                                        requirement("Python", "MUST", null, "Python", null),
                                        requirement("SQL", "MUST", null, "SQL", null))),
                        "Java\nPython\nSQL");
        assertThat(result.requirements()).hasSize(2);
        assertThat(result.droppedRequirements()).isEqualTo(1);
    }
}
