package ats.extraction;

import ats.extraction.infrastructure.ExtractionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Точка подключения библиотеки в приложении-сервисе:
 *
 * <pre>{@code
 * @SpringBootApplication
 * @Import(TextExtractionConfiguration.class)
 * public class VacancyServiceApplication { ... }
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"ats.extraction.domain", "ats.extraction.infrastructure"})
@EnableConfigurationProperties(ExtractionProperties.class)
public class TextExtractionConfiguration {}
