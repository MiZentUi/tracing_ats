package ats.resume.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.scoring")
public class ScoringProperties {

    /** Отдельный пул, чтобы скоринг при загрузке не конкурировал со скринингом пачки. */
    @Min(1)
    private int workerThreads = 4;

    @Min(1)
    private int queueCapacity = 1000;
}
