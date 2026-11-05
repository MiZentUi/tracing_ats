package ats.extraction.domain;

import java.util.List;
import java.util.Objects;

public record ExtractedText(
        String text,
        FileType type,
        Integer pageCount,
        List<ExtractionWarning> warnings) {

    public ExtractedText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(type, "type");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
