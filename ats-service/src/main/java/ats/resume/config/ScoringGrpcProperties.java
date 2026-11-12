package ats.resume.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.scoring.grpc")
public class ScoringGrpcProperties {

    private String target = "localhost:8082";

    /** Таймаут одного вызова ScoreResume. Внутри scoring-service может вызываться LLM. */
    private Duration deadline = Duration.ofSeconds(60);
}
