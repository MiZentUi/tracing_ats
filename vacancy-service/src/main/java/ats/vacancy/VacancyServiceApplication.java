package ats.vacancy;

import ats.extraction.TextExtractionConfiguration;
import ats.vacancy.screening.ScreeningPolicyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TextExtractionConfiguration.class)
@EnableConfigurationProperties(ScreeningPolicyProperties.class)
public class VacancyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VacancyServiceApplication.class, args);
    }
}
