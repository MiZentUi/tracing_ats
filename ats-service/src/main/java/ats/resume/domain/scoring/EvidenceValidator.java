package ats.resume.domain.scoring;

import java.util.List;
import java.util.Optional;

/**
 * Проверяет цитату-доказательство как подстроку обезличенного текста. Непроверенная цитата
 * отбрасывается: требование остаётся без покрытия, но балл не поднимается.
 */
public final class EvidenceValidator {

    private EvidenceValidator() {}

    public static Optional<String> validate(String quote, String text) {
        if (quote == null || quote.isBlank() || text == null || text.isEmpty()) {
            return Optional.empty();
        }
        String trimmed = quote.strip();
        if (trimmed.isEmpty() || !text.contains(trimmed)) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
    }

    public static List<String> validateAll(List<String> quotes, String text) {
        return quotes.stream()
                .map(quote -> validate(quote, text))
                .flatMap(Optional::stream)
                .toList();
    }
}
