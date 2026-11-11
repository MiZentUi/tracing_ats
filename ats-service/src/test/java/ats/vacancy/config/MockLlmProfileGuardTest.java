package ats.vacancy.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MockLlmProfileGuardTest {

    @Test
    void rejectsExternalHost() {
        VacancyLlmProperties properties = new VacancyLlmProperties();
        properties.setBaseUrl("https://openrouter.ai/api/v1");
        assertThatThrownBy(() -> new MockLlmProfileGuard(properties).validateBaseUrl())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local mock host");
    }

    @Test
    void acceptsLocalHostAndComposeService() {
        VacancyLlmProperties local = new VacancyLlmProperties();
        local.setBaseUrl("http://localhost:8089/api/v1");
        assertThatCode(() -> new MockLlmProfileGuard(local).validateBaseUrl())
                .doesNotThrowAnyException();

        VacancyLlmProperties compose = new VacancyLlmProperties();
        compose.setBaseUrl("http://mock-llm:8089/api/v1");
        assertThatCode(() -> new MockLlmProfileGuard(compose).validateBaseUrl())
                .doesNotThrowAnyException();
    }
}
