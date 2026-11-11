package ats.vacancy.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VacancyLlmPropertiesTest {

    @Test
    void doesNotLeakApiKeyInToString() {
        VacancyLlmProperties properties = new VacancyLlmProperties();
        properties.setApiKey("super-secret-key");
        assertThat(properties.toString()).doesNotContain("super-secret-key");
        assertThat(properties.toString()).contains("apiKey=***");
    }
}
