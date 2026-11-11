package ats.vacancy.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "vacancy.template")
public class TemplateProperties {

    @Min(1)
    private int maxTextChars = 30_000;

    @NotNull private DataSize maxFileSize = DataSize.ofMegabytes(5);

    @Valid @NotNull private KindHistory kindHistory = new KindHistory();

    @Getter
    @Setter
    public static class KindHistory {

        @Min(1)
        private int minObservations = 3;

        @DecimalMin(value = "0.5", inclusive = false)
        @DecimalMax("1.0")
        private double minShare = 0.6d;
    }

    @AssertTrue(message = "vacancy.template.kind-history.min-share must be in (0.5; 1]")
    public boolean isMinShareValid() {
        return kindHistory != null
                && kindHistory.getMinShare() > 0.5
                && kindHistory.getMinShare() <= 1.0;
    }
}
