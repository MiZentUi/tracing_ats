package ats.extraction.domain;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Приводит сырой текст к единому виду. Класс не хранит состояние и потокобезопасен. */
@Component
public class TextNormalizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern ZERO_WIDTH_CHARS =
            Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");
    private static final Pattern MULTIPLE_NEW_LINES = Pattern.compile("\\n{3,}");

    public String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String value = text.replace("\r\n", "\n").replace('\r', '\n');
        value = Normalizer.normalize(value, Normalizer.Form.NFC);
        value = value.replace('\u00A0', ' ');
        value = ZERO_WIDTH_CHARS.matcher(value).replaceAll("");
        value = CONTROL_CHARS.matcher(value).replaceAll("");
        value = stripTrailingWhitespace(value);
        value = MULTIPLE_NEW_LINES.matcher(value).replaceAll("\n\n");
        return value.strip();
    }

    private static String stripTrailingWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        int lineStart = 0;

        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '\n') {
                continue;
            }
            int lineEnd = i;
            while (lineEnd > lineStart && isHorizontalWhitespace(value.charAt(lineEnd - 1))) {
                lineEnd--;
            }
            result.append(value, lineStart, lineEnd).append('\n');
            lineStart = i + 1;
        }

        int lineEnd = value.length();
        while (lineEnd > lineStart && isHorizontalWhitespace(value.charAt(lineEnd - 1))) {
            lineEnd--;
        }
        result.append(value, lineStart, lineEnd);
        return result.toString();
    }

    private static boolean isHorizontalWhitespace(char value) {
        return value == ' ' || value == '\t';
    }
}
