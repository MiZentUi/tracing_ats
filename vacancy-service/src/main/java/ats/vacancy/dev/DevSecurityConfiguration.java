package ats.vacancy.dev;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Временно открывает dev-эндпоинт без Basic Auth, но только когда он явно включён флагом
 * ats.dev.extraction-endpoint.enabled=true.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "ats.dev.extraction-endpoint",
        name = "enabled",
        havingValue = "true")
public class DevSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/v1/dev/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .build();
    }
}
