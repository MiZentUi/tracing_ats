package ats.vacancy;

import ats.extraction.TextExtractionConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TextExtractionConfiguration.class)
public class VacancyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VacancyServiceApplication.class, args);
    }
}
