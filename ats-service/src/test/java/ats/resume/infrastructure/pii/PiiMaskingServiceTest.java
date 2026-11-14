package ats.resume.infrastructure.pii;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.domain.privacy.MaskingResult;
import ats.resume.domain.privacy.PiiType;
import ats.resume.infrastructure.pii.ner.NerEntity;
import ats.resume.infrastructure.pii.ner.NerModel;
import ats.resume.infrastructure.pii.ner.NerPiiDetector;
import ats.resume.infrastructure.pii.ner.NoOpNerModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiiMaskingServiceTest {

    @Test
    void masksStructuredAndContextualPiiBeforeScoring() {
        String text =
                """
                Иван Петров
                Email: ivan.petrov@example.com
                Телефон: +7 999 123-45-67
                Высшее образование
                Московский государственный университет
                Специальность: Прикладная математика
                2018-2022
                Опыт работы
                Java, Spring Boot, PostgreSQL
                """;

        NerModel ner =
                new NerModel() {
                    @Override
                    public List<NerEntity> detect(String value) {
                        return List.of(
                                new NerEntity(
                                        NerEntity.PERSON,
                                        value.indexOf("Иван Петров"),
                                        value.indexOf("Иван Петров") + "Иван Петров".length(),
                                        0.99),
                                new NerEntity(
                                        NerEntity.ORGANIZATION,
                                        value.indexOf("Московский государственный университет"),
                                        value.indexOf("Московский государственный университет")
                                                + "Московский государственный университет".length(),
                                        0.95));
                    }

                    @Override
                    public String modelName() {
                        return "test-ner";
                    }

                    @Override
                    public String modelHash() {
                        return "test";
                    }
                };

        EducationExtractor educationExtractor = new EducationExtractor();
        PiiMaskingService service =
                new PiiMaskingService(
                        List.of(
                                new RegexPiiDetector(),
                                new NerPiiDetector(ner, new ats.resume.config.PiiProperties())),
                        new PiiSpanResolver(),
                        educationExtractor,
                        new GitHubUrlExtractor());

        MaskingResult result = service.mask(text, "resume.pdf", null);

        assertThat(result.maskedText()).doesNotContain("ivan.petrov@example.com");
        assertThat(result.maskedText()).doesNotContain("+7 999 123-45-67");
        assertThat(result.maskedText()).doesNotContain("Иван Петров");
        assertThat(result.maskedText()).contains("[PII_EMAIL_1]");
        assertThat(result.maskedText()).contains("[PII_NAME_1]");
        // Образование не маскируется: вуз остаётся в тексте и не попадает в PII-payload.
        assertThat(result.maskedText()).contains("Московский государственный университет");
        assertThat(result.maskedText()).doesNotContain("[PII_UNIVERSITY_1]");
        assertThat(result.maskedText()).doesNotContain("[PII_GRADUATION_YEAR_1]");
        assertThat(result.payload().originalFilename()).isEqualTo("resume.pdf");
        assertThat(result.payload().university()).isNull();
        assertThat(result.payload().graduationYear()).isEqualTo(2022);
        assertThat(result.education().years()).isEqualTo(4);
        assertThat(
                        result.payload().entries().stream()
                                .anyMatch(entry -> entry.type() == PiiType.PHONE))
                .isTrue();
    }

    @Test
    void masksWholeWordWhenNerReturnsOnlyPartOfName() {
        String text = "Иванов Иван\nJava, Spring Boot";
        NerModel ner =
                new NerModel() {
                    @Override
                    public List<NerEntity> detect(String value) {
                        int start = value.indexOf("ван");
                        return List.of(
                                new NerEntity(
                                        NerEntity.PERSON, start, start + "ван".length(), 0.99));
                    }

                    @Override
                    public String modelName() {
                        return "test-ner";
                    }

                    @Override
                    public String modelHash() {
                        return "test";
                    }
                };

        PiiMaskingService service =
                new PiiMaskingService(
                        List.of(
                                new RegexPiiDetector(),
                                new NerPiiDetector(ner, new ats.resume.config.PiiProperties())),
                        new PiiSpanResolver(),
                        new EducationExtractor(),
                        new GitHubUrlExtractor());

        MaskingResult result = service.mask(text, "resume.txt", null);

        assertThat(result.maskedText()).doesNotContain("Иванов");
        assertThat(result.maskedText()).contains("[PII_NAME_1] Иван");
    }

    @Test
    void keepsEducationBlockWithoutNer() {
        String text =
                """
                Высшее образование
                Московский государственный университет
                2018-2022
                Опыт работы
                Java, Spring Boot
                """;

        EducationExtractor educationExtractor = new EducationExtractor();
        PiiMaskingService service =
                new PiiMaskingService(
                        List.of(
                                new RegexPiiDetector(),
                                new NerPiiDetector(
                                        new NoOpNerModel(), new ats.resume.config.PiiProperties())),
                        new PiiSpanResolver(),
                        educationExtractor,
                        new GitHubUrlExtractor());

        MaskingResult result = service.mask(text, "resume.txt", null);

        assertThat(result.maskedText()).contains("Московский государственный университет");
        assertThat(result.maskedText()).doesNotContain("[PII_UNIVERSITY_1]");
        assertThat(result.payload().university()).isNull();
        assertThat(result.payload().graduationYear()).isEqualTo(2022);
    }
}
