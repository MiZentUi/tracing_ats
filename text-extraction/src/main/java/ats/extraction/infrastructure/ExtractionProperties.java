package ats.extraction.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.ingestion.extraction")
public class ExtractionProperties {

    @NotNull private DataSize maxFileSize = DataSize.ofMegabytes(10);

    @Min(1)
    private int maxPdfPages = 50;

    @Min(1)
    private int maxTextChars = 200_000;

    @NotNull private Duration timeout = Duration.ofSeconds(10);

    @Min(1)
    private int poolSize = 4;

    @Valid @NotNull private Docx docx = new Docx();

    @AssertTrue(message = "ats.ingestion.extraction.max-file-size must be positive")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }

    @AssertTrue(message = "ats.ingestion.extraction.timeout must be positive")
    public boolean isTimeoutPositive() {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }

    @Getter
    @Setter
    public static class Docx {

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minInflateRatio = 0.01d;

        @NotNull private DataSize maxEntrySize = DataSize.ofMegabytes(50);

        @AssertTrue(message = "ats.ingestion.extraction.docx.max-entry-size must be positive")
        public boolean isMaxEntrySizePositive() {
            return maxEntrySize != null && maxEntrySize.toBytes() > 0;
        }
    }
}
