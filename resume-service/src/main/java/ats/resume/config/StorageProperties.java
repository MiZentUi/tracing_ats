package ats.resume.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.storage")
public class StorageProperties {

    public enum Type {
        S3,
        MEMORY
    }

    @NotNull private Type type = Type.S3;

    @NotBlank private String endpoint = "http://localhost:9000";

    @NotBlank private String accessKey = "minioadmin";

    @NotBlank private String secretKey = "minioadmin";

    @NotBlank private String bucket = "ats-resumes";

    @NotBlank private String region = "us-east-1";

    @NotNull private Duration timeout = Duration.ofSeconds(10);
}
