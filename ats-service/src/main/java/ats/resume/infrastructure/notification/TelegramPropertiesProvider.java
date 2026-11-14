package ats.resume.infrastructure.notification;

import ats.resume.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Тонкая обёртка, чтобы outbox не зависел от конкретного канала. */
@Component
@RequiredArgsConstructor
public class TelegramPropertiesProvider {

    private final TelegramProperties properties;

    public boolean enabled() {
        return properties.isConfigured();
    }

    public int maxAttempts() {
        return properties.getMaxAttempts();
    }

    public java.time.Duration retryDelay() {
        return properties.getRetryDelay();
    }
}
