package ats.vacancy.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ats.extraction.domain.TextExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevExtractionConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(TextExtractionService.class, () -> mock(TextExtractionService.class))
            .withUserConfiguration(DevExtractionController.class);

    @Test
    void controllerIsAbsentByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DevExtractionController.class));
    }

    @Test
    void controllerAppearsOnlyWithEnabledFlag() {
        contextRunner
                .withPropertyValues("ats.dev.extraction-endpoint.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DevExtractionController.class));
    }
}
