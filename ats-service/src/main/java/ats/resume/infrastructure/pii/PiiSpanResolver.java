package ats.resume.infrastructure.pii;

import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Разрешает пересечения детекторов. Пересекающиеся спаны объединяются в один:
 * лучше замаскировать
 * лишнее, чем пропустить PII. Тип выбирается по приоритету, чтобы ссылка на
 * GitHub не стала
 * безликим URL.
 *
 * <p>
 * Перед объединением каждый спан расширяется до границы слова. Модель иногда
 * возвращает только
 * часть имени («ВАН» внутри «Иванов»); без расширения остаток слова попадал бы
 * в обезличенный
 * текст.
 */
@Component
public class PiiSpanResolver {

    private static final Map<PiiType, Integer> PRIORITY =
            Map.ofEntries(
                    Map.entry(PiiType.PASSPORT, 100),
                    Map.entry(PiiType.SNILS, 100),
                    Map.entry(PiiType.INN, 100),
                    Map.entry(PiiType.EMAIL, 90),
                    Map.entry(PiiType.PHONE, 90),
                    Map.entry(PiiType.GITHUB, 80),
                    Map.entry(PiiType.LINKEDIN, 80),
                    Map.entry(PiiType.TELEGRAM, 80),
                    Map.entry(PiiType.UNIVERSITY, 70),
                    Map.entry(PiiType.GRADUATION_YEAR, 70),
                    Map.entry(PiiType.NAME, 60),
                    Map.entry(PiiType.LOCATION, 55),
                    Map.entry(PiiType.URL, 50),
                    Map.entry(PiiType.BIRTH_DATE, 45),
                    Map.entry(PiiType.AGE, 40),
                    Map.entry(PiiType.GENDER, 40),
                    Map.entry(PiiType.MARITAL_STATUS, 40),
                    Map.entry(PiiType.PHOTO, 30));

    public List<PiiSpan> resolve(String text, List<PiiSpan> spans) {
        if (spans.isEmpty()) {
            return List.of();
        }
        List<PiiSpan> sorted = new ArrayList<>(spans.size());
        for (PiiSpan span : spans) {
            sorted.add(expandToWordBoundary(text, span));
        }
        sorted.sort(
                Comparator.comparingInt(PiiSpan::start)
                        .thenComparing(Comparator.comparingInt(PiiSpan::length).reversed()));

        List<PiiSpan> result = new ArrayList<>();
        PiiSpan current = sorted.getFirst();
        for (int index = 1; index < sorted.size(); index++) {
            PiiSpan next = sorted.get(index);
            boolean overlap = next.start() < current.end();
            boolean sameTypeAdjacent =
                    next.start() == current.end() && next.type() == current.type();
            if (overlap || sameTypeAdjacent) {
                PiiType type =
                        priority(next.type()) > priority(current.type())
                                ? next.type()
                                : current.type();
                current =
                        new PiiSpan(
                                Math.min(current.start(), next.start()),
                                Math.max(current.end(), next.end()),
                                type,
                                Math.max(current.confidence(), next.confidence()),
                                current.source());
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        return result;
    }

    private PiiSpan expandToWordBoundary(String text, PiiSpan span) {
        if (text == null
                || span.start() < 0
                || span.end() > text.length()
                || span.start() >= span.end()) {
            return span;
        }
        int start = span.start();
        int end = span.end();
        while (start > 0) {
            int codePoint = text.codePointBefore(start);
            if (!isWordChar(codePoint)) {
                break;
            }
            start -= Character.charCount(codePoint);
        }
        while (end < text.length()) {
            int codePoint = text.codePointAt(end);
            if (!isWordChar(codePoint)) {
                break;
            }
            end += Character.charCount(codePoint);
        }
        if (start == span.start() && end == span.end()) {
            return span;
        }
        return new PiiSpan(start, end, span.type(), span.confidence(), span.source());
    }

    private static boolean isWordChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == '-';
    }

    private int priority(PiiType type) {
        return PRIORITY.getOrDefault(type, 0);
    }
}
