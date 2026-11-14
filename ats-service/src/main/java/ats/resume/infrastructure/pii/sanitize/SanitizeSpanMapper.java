package ats.resume.infrastructure.pii.sanitize;

import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import ats.sanitize.proto.PiiEntity;
import ats.sanitize.proto.PiiEntityType;
import java.util.ArrayList;
import java.util.List;

/** Переводит ответ sanitize-service в доменные спаны. Не логирует текст и сущности. */
final class SanitizeSpanMapper {

    private SanitizeSpanMapper() {}

    static List<PiiSpan> toSpans(String text, List<PiiEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        int codePoints = text.codePointCount(0, text.length());
        List<PiiSpan> spans = new ArrayList<>(entities.size());
        for (PiiEntity entity : entities) {
            PiiType type = type(entity.getType());
            if (type == null) {
                continue;
            }
            int start = entity.getStart();
            int end = entity.getEnd();
            if (start < 0 || end <= start || end > codePoints) {
                continue;
            }
            int charStart = text.offsetByCodePoints(0, start);
            int charEnd = text.offsetByCodePoints(0, end);
            spans.add(
                    new PiiSpan(
                            charStart,
                            charEnd,
                            type,
                            clamp(entity.getConfidence()),
                            PiiSpan.Source.SANITIZE));
        }
        return spans;
    }

    static PiiType type(PiiEntityType type) {
        return switch (type) {
            case PII_TYPE_NAME -> PiiType.NAME;
            case PII_TYPE_EMAIL -> PiiType.EMAIL;
            case PII_TYPE_PHONE -> PiiType.PHONE;
            case PII_TYPE_URL -> PiiType.URL;
            case PII_TYPE_TELEGRAM -> PiiType.TELEGRAM;
            case PII_TYPE_LINKEDIN -> PiiType.LINKEDIN;
            case PII_TYPE_GITHUB -> PiiType.GITHUB;
            case PII_TYPE_PASSPORT -> PiiType.PASSPORT;
            case PII_TYPE_SNILS -> PiiType.SNILS;
            case PII_TYPE_INN -> PiiType.INN;
            case PII_TYPE_BIRTH_DATE -> PiiType.BIRTH_DATE;
            case PII_TYPE_AGE -> PiiType.AGE;
            case PII_TYPE_GENDER -> PiiType.GENDER;
            case PII_TYPE_LOCATION -> PiiType.LOCATION;
            case PII_TYPE_UNIVERSITY -> PiiType.UNIVERSITY;
            case PII_TYPE_GRADUATION_YEAR -> PiiType.GRADUATION_YEAR;
            case PII_TYPE_MARITAL_STATUS -> PiiType.MARITAL_STATUS;
            case PII_TYPE_PHOTO -> PiiType.PHOTO;
            default -> null;
        };
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
