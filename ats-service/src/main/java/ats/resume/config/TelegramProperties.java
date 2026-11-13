package ats.resume.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Telegram-алерты. Токен и chat id задаются только через окружение. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.telegram")
public class TelegramProperties {

    private boolean enabled = false;

    private String botToken = "";

    private String chatId = "";

    @NotNull private String baseUrl = "https://api.telegram.org";

    @NotNull private Duration timeout = Duration.ofSeconds(10);

    @Min(1)
    private int maxAttempts = 3;

    @NotNull private Duration retryDelay = Duration.ofSeconds(30);

    /** База для ссылки на вакансию в сообщении. */
    private String frontendBaseUrl = "http://localhost:3000";

    public boolean isConfigured() {
        return enabled
                && botToken != null
                && !botToken.isBlank()
                && chatId != null
                && !chatId.isBlank();
    }
}
