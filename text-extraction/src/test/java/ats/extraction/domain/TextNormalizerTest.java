package ats.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void convertsLineEndingsToLf() {
        assertThat(normalizer.normalize("a\r\nb\rc")).isEqualTo("a\nb\nc");
    }

    @Test
    void appliesUnicodeNfc() {
        assertThat(normalizer.normalize("e\u0301")).isEqualTo("\u00e9");
    }

    @Test
    void removesBomAndZeroWidthCharacters() {
        assertThat(normalizer.normalize("\uFEFFa\u200Bb\u200Dc\u2060d")).isEqualTo("abcd");
    }

    @Test
    void replacesNonBreakingSpaces() {
        assertThat(normalizer.normalize("a\u00A0b")).isEqualTo("a b");
    }

    @Test
    void removesControlCharactersButKeepsTabsAndNewLines() {
        assertThat(normalizer.normalize("a\u0000b\tc\nd\u0007e")).isEqualTo("ab\tc\nde");
    }

    @Test
    void stripsTrailingWhitespaceOnEveryLine() {
        assertThat(normalizer.normalize("a   \nb\t\t\nc  ")).isEqualTo("a\nb\nc");
    }

    @Test
    void collapsesThreeOrMoreNewLines() {
        assertThat(normalizer.normalize("a\n\n\n\nb")).isEqualTo("a\n\nb");
    }

    @Test
    void trimsWholeText() {
        assertThat(normalizer.normalize("\n  a  \n")).isEqualTo("a");
    }

    @Test
    void returnsEmptyStringForNull() {
        assertThat(normalizer.normalize(null)).isEmpty();
    }
}
