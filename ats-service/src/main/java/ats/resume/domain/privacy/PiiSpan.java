package ats.resume.domain.privacy;

import java.util.Objects;

/** Найденный фрагмент PII. Координаты — в исходном нормализованном тексте. */
public record PiiSpan(int start, int end, PiiType type, double confidence, Source source) {

    public PiiSpan {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("invalid PII span: " + start + ".." + end);
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
    }

    public int length() {
        return end - start;
    }

    public enum Source {
        REGEX,
        NER,
        LABEL,
        EDUCATION,
        /** Результат внешнего локального слоя, например sanitize-service. */
        SANITIZE
    }
}
