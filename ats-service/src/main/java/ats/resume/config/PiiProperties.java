package ats.resume.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.pii")
public class PiiProperties {

    /** Base64-ключ AES-256. Читается только из окружения, в коде нет значения по умолчанию. */
    private String encryptionKey = "";

    @NotBlank private String keyVersion = "v1";

    @Valid @NotNull private Ner ner = new Ner();

    @Getter
    @Setter
    public static class Ner {

        public enum Provider {
            ONNX,
            FAKE
        }

        @NotNull private Provider provider = Provider.FAKE;

        @NotNull private Path modelPath = Path.of("models/ner/model.onnx");

        @NotNull private Path tokenizerPath = Path.of("models/ner/tokenizer.json");

        @NotNull private Path configPath = Path.of("models/ner/config.json");

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double confidenceThreshold = 0.85;

        /** В prod при недоступной модели текст не должен уходить в scoring. */
        private boolean failClosed = true;
    }
}
