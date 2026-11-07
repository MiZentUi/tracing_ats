package ats.extraction.domain;

import java.util.List;
import java.util.Objects;

/**
 * Сырой результат экстрактора до нормализации. Метаданные нужны, чтобы сервис мог вернуть число
 * страниц и предупреждения, не завися от конкретного парсера.
 */
public record RawExtraction(String text, Integer pageCount, List<ExtractionWarning> warnings) {

    public RawExtraction {
        Objects.requireNonNull(text, "text");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static RawExtraction text(String text) {
        return new RawExtraction(text, null, List.of());
    }
}
