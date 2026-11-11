package ats;

import ats.extraction.TextExtractionConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/**
 * Единственный Spring Boot сервис проекта: вакансии, шаблон, справочники, резюме, маскировка PII,
 * скрининг, рейтинг, отчёт и раскрытие PII живут в одном процессе.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("ats")
@Import(TextExtractionConfiguration.class)
public class AtsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtsServiceApplication.class, args);
    }
}
