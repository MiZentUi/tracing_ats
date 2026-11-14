package ats.resume.infrastructure.pii;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PiiSpanResolverTest {

    private final PiiSpanResolver resolver = new PiiSpanResolver();

    @Test
    void expandsPartialNameToWordBoundary() {
        String text = "Иванов Иван";

        List<PiiSpan> resolved =
                resolver.resolve(
                        text, List.of(new PiiSpan(1, 4, PiiType.NAME, 0.9, PiiSpan.Source.NER)));

        assertThat(resolved)
                .containsExactly(new PiiSpan(0, 6, PiiType.NAME, 0.9, PiiSpan.Source.NER));
    }

    @Test
    void keepsSpanThatAlreadyCoversWholeWord() {
        String text = "Иванов Иван";

        List<PiiSpan> resolved =
                resolver.resolve(
                        text, List.of(new PiiSpan(0, 6, PiiType.NAME, 0.9, PiiSpan.Source.NER)));

        assertThat(resolved)
                .containsExactly(new PiiSpan(0, 6, PiiType.NAME, 0.9, PiiSpan.Source.NER));
    }

    @Test
    void doesNotIncludeTrailingPunctuation() {
        String text = "Иванов, Иван";

        List<PiiSpan> resolved =
                resolver.resolve(
                        text, List.of(new PiiSpan(1, 4, PiiType.NAME, 0.9, PiiSpan.Source.NER)));

        assertThat(resolved)
                .containsExactly(new PiiSpan(0, 6, PiiType.NAME, 0.9, PiiSpan.Source.NER));
    }

    @Test
    void mergesSpansThatOverlapAfterExpansion() {
        String text = "Иванов";

        List<PiiSpan> resolved =
                resolver.resolve(
                        text,
                        List.of(
                                new PiiSpan(1, 3, PiiType.NAME, 0.8, PiiSpan.Source.NER),
                                new PiiSpan(2, 5, PiiType.NAME, 0.9, PiiSpan.Source.NER)));

        assertThat(resolved)
                .containsExactly(new PiiSpan(0, 6, PiiType.NAME, 0.9, PiiSpan.Source.NER));
    }

    @Test
    void expandsLatinWordWithSameRule() {
        String text = "Ivanov Ivan";

        List<PiiSpan> resolved =
                resolver.resolve(
                        text, List.of(new PiiSpan(1, 4, PiiType.NAME, 0.9, PiiSpan.Source.NER)));

        assertThat(resolved)
                .containsExactly(new PiiSpan(0, 6, PiiType.NAME, 0.9, PiiSpan.Source.NER));
    }
}
