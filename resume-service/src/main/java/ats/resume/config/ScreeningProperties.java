package ats.resume.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.screening")
public class ScreeningProperties {

    @Min(1)
    private int workerThreads = 2;

    /** Сколько ждать предварительный скор, который ещё считается после загрузки. */
    @NotNull private Duration scoringWait = Duration.ofMinutes(2);

    @NotNull private Duration scoringPoll = Duration.ofMillis(500);
}
