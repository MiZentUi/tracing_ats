package ats.resume.infrastructure.pii;

import java.util.List;
import java.util.Locale;

/**
 * Контекст блока образования. Нужен, чтобы ORG-модель не путала вуз с работодателем: компания — это
 * опыт, вуз — PII. Словарь вузов не используется, только несколько контекстных слов.
 */
public final class EducationContext {

    private static final List<String> EDUCATION_KEYWORDS =
            List.of(
                    "образование",
                    "education",
                    "университет",
                    "институт",
                    "академия",
                    "колледж",
                    "university",
                    "institute",
                    "academy",
                    "college");

    private static final List<String> SECTION_END_KEYWORDS =
            List.of(
                    "опыт работы",
                    "опыт",
                    "experience",
                    "навыки",
                    "skills",
                    "проекты",
                    "projects",
                    "сертификаты",
                    "certificates",
                    "языки",
                    "languages",
                    "дополнительно",
                    "additional",
                    "публикации",
                    "publications");

    private EducationContext() {}

    public static boolean isEducationLine(String text, int index) {
        int[] line = lineBounds(text, index);
        String value = text.substring(line[0], line[1]).toLowerCase(Locale.ROOT);
        return containsAny(value, EDUCATION_KEYWORDS);
    }

    public static boolean isUniversityLine(String text, int index) {
        int[] line = lineBounds(text, index);
        String value = text.substring(line[0], line[1]).toLowerCase(Locale.ROOT);
        return containsAny(
                value,
                List.of(
                        "университет",
                        "институт",
                        "академия",
                        "колледж",
                        "university",
                        "institute",
                        "academy",
                        "college"));
    }

    public static boolean isInEducationBlock(String text, int index) {
        int[] line = lineBounds(text, index);
        int cursor = line[0];
        int checked = 0;
        while (cursor > 0 && checked < 20) {
            int previousEnd = cursor - 1;
            int[] previous = lineBounds(text, previousEnd);
            String value = text.substring(previous[0], previous[1]).toLowerCase(Locale.ROOT);
            if (containsAny(value, EDUCATION_KEYWORDS)) {
                return true;
            }
            if (containsAny(value, SECTION_END_KEYWORDS) && !value.contains("образование")) {
                return false;
            }
            cursor = previous[0];
            checked++;
        }
        return false;
    }

    public static int[] educationBlock(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int start = -1;
        for (String keyword : EDUCATION_KEYWORDS) {
            int index = lower.indexOf(keyword);
            if (index >= 0 && (start < 0 || index < start)) {
                start = index;
            }
        }
        if (start < 0) {
            return null;
        }
        int blockStart = lineBounds(text, start)[0];
        int blockEnd = text.length();
        int cursor = lineBounds(text, start)[1];
        while (cursor < text.length()) {
            int nextStart = cursor + 1;
            if (nextStart >= text.length()) {
                break;
            }
            int[] nextLine = lineBounds(text, nextStart);
            String value = text.substring(nextLine[0], nextLine[1]).toLowerCase(Locale.ROOT);
            if (containsAny(value, SECTION_END_KEYWORDS)
                    && !containsAny(value, EDUCATION_KEYWORDS)) {
                blockEnd = nextLine[0];
                break;
            }
            cursor = nextLine[1];
        }
        return new int[] {blockStart, blockEnd};
    }

    public static int[] lineBounds(String text, int index) {
        int start = text.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int end = text.indexOf('\n', index);
        if (end < 0) {
            end = text.length();
        }
        return new int[] {start, end};
    }

    private static boolean containsAny(String value, List<String> keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
