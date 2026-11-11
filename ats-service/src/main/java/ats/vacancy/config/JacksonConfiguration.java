package ats.vacancy.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 использует Jackson 3 для HTTP, а Spring AI и разбор ответа LLM работают с Jackson
 * 2. Даём явный бин Jackson 2, чтобы не зависеть от автоконфигурации.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
