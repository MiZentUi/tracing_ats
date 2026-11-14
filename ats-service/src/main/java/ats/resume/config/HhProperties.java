package ats.resume.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки интеграции с hh.ru. Токен работодателя и секрет вебхука приходят только из окружения.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.hh")
public class HhProperties {

    private boolean enabled = false;

    @NotNull private String apiBaseUrl = "https://api.hh.ru";

    /** OAuth-токен работодателя. Не логируется. */
    private String token = "";

    /** Идентификатор работодателя: защищает от привязки чужой вакансии. */
    private String employerId = "";

    /** Секрет в query-параметре вебхука. Не логируется. */
    private String webhookToken = "";

    /** Полный публичный URL вебхука, включая token. Не логируется. */
    private String webhookPublicUrl = "";

    /** Ожидаемый subscription_id, если задан: отсекает поддельные события. */
    private String subscriptionId = "";

    /** User-Agent с контактом: требование hh.ru. */
    private String userAgent = "tracing-ats/0.1 (ats@example.com)";

    /** Значения по умолчанию для публикации: в MVP маппинг справочников неполный. */
    private String defaultArea = "1";

    private String defaultExperience = "between1And3";

    private String defaultEmploymentForm = "FULL";

    private String defaultSchedule = "fullDay";

    private String defaultProfessionalRole = "96";

    @NotNull private Duration timeout = Duration.ofSeconds(10);

    @Min(1)
    private int maxAttempts = 3;

    @NotNull private Duration retryDelay = Duration.ofSeconds(2);

    public boolean isConfigured() {
        return enabled
                && token != null
                && !token.isBlank()
                && webhookToken != null
                && !webhookToken.isBlank()
                && webhookPublicUrl != null
                && !webhookPublicUrl.isBlank();
    }
}
