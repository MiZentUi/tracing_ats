package ats.vacancy.dictionary;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ats.dictionary.grpc")
public class DictionaryProperties {

    /** Пока false — используется локальный словарь в БД ats. */
    private boolean enabled = false;

    private String target = "localhost:8084";

    private Duration deadline = Duration.ofSeconds(2);
}
