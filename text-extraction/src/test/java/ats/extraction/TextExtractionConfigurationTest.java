package ats.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import ats.extraction.domain.TextExtractionService;
import ats.extraction.infrastructure.ExtractionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TextExtractionConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TextExtractionConfiguration.class);

    @Test
    void registersAllBeansAndBindsProperties() {
        contextRunner
                .withPropertyValues(
                        "ats.ingestion.extraction.max-text-chars=123",
                        "ats.ingestion.extraction.timeout=5s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TextExtractionService.class);
                    assertThat(context.getBean(ExtractionProperties.class).getMaxTextChars())
                            .isEqualTo(123);
                    assertThat(context.getBean(ExtractionProperties.class).getTimeout())
                            .hasSeconds(5);
                });
    }

    @Test
    void failsOnNonPositiveTextLimit() {
        contextRunner
                .withPropertyValues("ats.ingestion.extraction.max-text-chars=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsOnNonPositiveFileSize() {
        contextRunner
                .withPropertyValues("ats.ingestion.extraction.max-file-size=0B")
                .run(context -> assertThat(context).hasFailed());
    }
}
