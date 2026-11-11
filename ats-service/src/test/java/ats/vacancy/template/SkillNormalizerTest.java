package ats.vacancy.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillNormalizerTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(SkillNormalizer.normalizeKey("js")).isEqualTo("js");
        assertThat(SkillNormalizer.normalizeKey("JS")).isEqualTo("js");
        assertThat(SkillNormalizer.normalizeKey(" js ")).isEqualTo("js");
        assertThat(SkillNormalizer.normalizeKey("JS,")).isEqualTo("js");
    }

    @Test
    void keepsMeaningfulSymbols() {
        assertThat(SkillNormalizer.normalizeKey("C++")).isEqualTo("c++");
        assertThat(SkillNormalizer.normalizeKey("C#")).isEqualTo("c#");
        assertThat(SkillNormalizer.normalizeKey(".NET")).isEqualTo(".net");
    }

    @Test
    void removesOnlyTrailingDot() {
        assertThat(SkillNormalizer.normalizeKey("Node.js.")).isEqualTo("node.js");
    }

    @Test
    void collapsesWhitespaceAndUnicode() {
        assertThat(SkillNormalizer.normalizeKey("  Spring \u00A0 Boot  ")).isEqualTo("spring boot");
        assertThat(SkillNormalizer.normalizeKey("«Python»")).isEqualTo("python");
    }
}
