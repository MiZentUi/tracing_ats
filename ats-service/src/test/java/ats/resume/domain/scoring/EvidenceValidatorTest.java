package ats.resume.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvidenceValidatorTest {

    @Test
    void keepsQuoteThatIsSubstring() {
        assertThat(EvidenceValidator.validate("Java, Spring Boot", "Опыт: Java, Spring Boot."))
                .contains("Java, Spring Boot");
    }

    @Test
    void dropsQuoteThatIsNotSubstring() {
        assertThat(EvidenceValidator.validate("Python", "Опыт: Java.")).isEmpty();
        assertThat(EvidenceValidator.validate("  ", "Опыт: Java.")).isEmpty();
        assertThat(EvidenceValidator.validate(null, "Опыт: Java.")).isEmpty();
    }
}
