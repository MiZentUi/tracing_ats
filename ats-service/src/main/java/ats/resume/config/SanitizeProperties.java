package ats.resume.config;

import ats.sanitize.proto.Layer;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Локальный слой маскировки PII через sanitize-service. По умолчанию выключен: CI и тесты не должны
 * требовать запущенный Python-сервис. Включается явно через {@code ats.pii.sanitize.enabled}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.pii.sanitize")
public class SanitizeProperties {

    private boolean enabled = false;

    @NotBlank private String target = "localhost:8083";

    /** Таймаут одного DetectPii. Локальный spaCy отвечает быстро; LLM-слой будет дольше. */
    @NotNull private Duration deadline = Duration.ofSeconds(3);

    /** Имя пайплайна из конфигурации sanitize-service. Пусто — пайплайн по умолчанию. */
    private String pipeline = "";

    /** Слои, которые нужно применить. Пусто — все слои выбранного пайплайна. */
    @NotNull private List<Layer> layers = List.of();

    /** Минимальная уверенность сущности, 0..1. 0 — порог из конфигурации сервиса. */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double minConfidence = 0.0;

    /** В prod при недоступном sanitize-service текст не должен уходить в scoring. */
    private boolean failClosed = true;
}
