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
@ConfigurationProperties(prefix = "ats.github")
public class GitHubProperties {

    private boolean enabled = false;

    private String token = "";

    @NotNull private String baseUrl = "https://api.github.com";

    @Min(1)
    private int maxRepos = 5;

    @NotNull private Duration timeout = Duration.ofSeconds(5);
}
