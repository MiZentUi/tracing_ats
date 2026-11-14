package ats.vacancy.template;

import static org.assertj.core.api.Assertions.assertThat;

import ats.vacancy.config.VacancyLlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class PromptLoaderTest {

    private final PromptLoader loader =
            new PromptLoader(new DefaultResourceLoader(), new VacancyLlmProperties());

    @Test
    void loadsActiveVacancyPrompt() {
        assertThat(loader.systemPrompt())
                .contains("Spring (Boot, Security)")
                .contains("Spring Boot")
                .contains("Spring Security")
                .contains("не добавляй отдельный навык «Spring»");
        assertThat(loader.jsonSchema()).contains("\"schema_version\"");
    }
}
