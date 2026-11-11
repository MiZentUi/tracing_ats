package ats.vacancy.template;

import java.text.Normalizer;
import java.util.Locale;

/** Чистая нормализация названия навыка. Словарь и БД сюда не входят. */
public final class SkillNormalizer {

    private static final String EDGE_CHARS = ",;:!?\"'()[]«»";

    private SkillNormalizer() {}

    public static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        value = value.replaceAll("[\\s\\u00A0\\u202F]+", " ").strip();
        value = value.toLowerCase(Locale.ROOT);

        int start = 0;
        int end = value.length();
        while (start < end && EDGE_CHARS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && EDGE_CHARS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        if (end > start && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(start, end).strip();
    }
}
