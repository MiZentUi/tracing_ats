package ats.resume.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ats.resume.match")
public class ResumeMatchProperties {

    /** Сколько вакансий уходит в один gRPC-вызов `EvaluateResumeMatches`. */
    @Min(1)
    private int batchSize = 3;
}
