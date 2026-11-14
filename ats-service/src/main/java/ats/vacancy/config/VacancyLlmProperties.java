package ats.vacancy.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Настройки внешней LLM. Значение ключа намеренно не попадает в {@link #toString()}. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "vacancy.llm")
public class VacancyLlmProperties {

    @NotBlank private String baseUrl = "https://openrouter.ai/api/v1";

    /** Может быть пустым: это ошибка вызова, а не старта приложения. */
    private String apiKey = "";

    @NotBlank private String model;

    private List<String> fallbackModels = new ArrayList<>();

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private Double temperature = 0.0d;

    @Min(1)
    private int maxTokens = 6000;

    @NotNull private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull private Duration readTimeout = Duration.ofSeconds(120);

    @NotNull private Duration totalTimeout = Duration.ofSeconds(170);

    @Min(1)
    private int maxAttempts = 3;

    @NotNull private Duration delay = Duration.ofSeconds(5);

    @DecimalMin("1.0")
    private double multiplier = 2.0d;

    @NotBlank private String promptVersion = "vacancy-template.v4";

    @Min(1)
    private int maxRequirements = 50;

    /** Запасной провайдер: Cloudflare Workers AI (OpenAI-совместимый endpoint). */
    @NotNull @Valid private Fallback fallback = new Fallback();

    @AssertTrue(message = "vacancy.llm.total-timeout must be greater than read-timeout")
    public boolean isTotalTimeoutGreaterThanReadTimeout() {
        return totalTimeout != null
                && readTimeout != null
                && totalTimeout.compareTo(readTimeout) > 0;
    }

    @AssertTrue(message = "vacancy.llm timeouts must be positive")
    public boolean isTimeoutsPositive() {
        return positive(connectTimeout) && positive(readTimeout) && positive(totalTimeout);
    }

    @AssertTrue(message = "vacancy.llm.delay must be positive")
    public boolean isDelayPositive() {
        return positive(delay);
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    @Override
    public String toString() {
        return "VacancyLlmProperties(baseUrl="
                + baseUrl
                + ", apiKey=***, model="
                + model
                + ", fallbackModels="
                + fallbackModels
                + ", promptVersion="
                + promptVersion
                + ", fallback="
                + fallback
                + ")";
    }

    @Getter
    @Setter
    public static class Fallback {

        private boolean enabled;

        private String baseUrl = "";

        /** Может быть пустым: это ошибка вызова, а не старта приложения. */
        private String apiKey = "";

        private String model = "@cf/qwen/qwen3.8-27b";

        private List<String> fallbackModels = new ArrayList<>();

        private String reasoningEffort = "low";

        @AssertTrue(
                message =
                        "vacancy.llm.fallback.base-url and model are required when fallback is enabled")
        public boolean isEndpointConfiguredWhenEnabled() {
            return !enabled
                    || (baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank());
        }

        @Override
        public String toString() {
            return "Fallback(enabled="
                    + enabled
                    + ", baseUrl="
                    + baseUrl
                    + ", apiKey=***, model="
                    + model
                    + ", fallbackModels="
                    + fallbackModels
                    + ", reasoningEffort="
                    + reasoningEffort
                    + ")";
        }
    }
}
