package ats.vacancy.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * В профиле mock-llm запросы должны уходить только на локальную заглушку. Иначе старт падает:
 * случайный запуск с профилем не должен отправить текст вакансии во внешний сервис.
 */
@Component
@Profile("mock-llm")
@RequiredArgsConstructor
public class MockLlmProfileGuard {

    private static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "mock-llm");

    private final VacancyLlmProperties properties;

    @PostConstruct
    void validateBaseUrl() {
        String host;
        try {
            host = URI.create(properties.getBaseUrl()).getHost();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "vacancy.llm.base-url is invalid in mock-llm profile: "
                            + properties.getBaseUrl(),
                    e);
        }
        if (host == null || !ALLOWED_HOSTS.contains(host)) {
            throw new IllegalStateException(
                    "vacancy.llm.base-url in mock-llm profile must point to a local mock host "
                            + ALLOWED_HOSTS
                            + ", but was: "
                            + properties.getBaseUrl());
        }
    }
}
