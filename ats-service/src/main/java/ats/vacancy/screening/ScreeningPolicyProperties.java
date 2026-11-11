package ats.vacancy.screening;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Политика шорт-листа. Порог и top-N — продуктовые настройки vacancy-service, не модели. Значения
 * по умолчанию — стартовые гипотезы для MVP.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.vacancy.screening")
public class ScreeningPolicyProperties {

    @DecimalMin("0")
    @DecimalMax("100")
    private double threshold = 50;

    @Min(1)
    private int topN = 10;

    @DecimalMin("0")
    @DecimalMax("100")
    private double borderlineMargin = 5;
}
