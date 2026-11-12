package ats.resume.domain.scoring;

/**
 * `ParsedResume` из scoring-service в base64-виде protobuf. Хранится в `parsed_resume`, чтобы
 * повторный расчёт не вызывал LLM повторно (инвариант 10).
 */
public record ParsedResume(String base64) {

    public ParsedResume {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("parsed resume is empty");
        }
    }
}
