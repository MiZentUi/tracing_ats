package ats.resume.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.vacancy.grpc")
public class VacancyGrpcProperties {

    @NotNull private String target = "localhost:9090";

    @NotNull private Duration deadline = Duration.ofSeconds(5);
}
