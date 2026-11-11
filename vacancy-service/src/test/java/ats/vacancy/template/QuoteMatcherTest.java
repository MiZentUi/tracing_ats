package ats.vacancy.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteMatcherTest {

    @Test
    void matchesAcrossWhitespaceVariants() {
        String text = "Опыт работы\nс  Java\u00A0от 3 лет";
        assertThat(QuoteMatcher.isSubstring("Опыт работы с Java от 3 лет", text)).isTrue();
    }

    @Test
    void missingQuoteDoesNotMatch() {
        assertThat(QuoteMatcher.isSubstring("Kafka", "Java и SQL")).isFalse();
    }
}
