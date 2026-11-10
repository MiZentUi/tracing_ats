package ats.resume.infrastructure.pii;

import ats.resume.domain.privacy.EducationFacts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Извлекает нормализованные факты об образовании. Годы обучения не считаются рабочим стажем: это
 * отдельный факт, который текущая модель скоринга не использует.
 */
@Component
public class EducationExtractor {

    private static final Pattern SPECIALTY =
            Pattern.compile(
                    "(?:специальность|specialty|major|направление(?:\\s+подготовки)?)"
                            + "\\s*[:\\-]?\\s*([^\\n]{2,120})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern YEAR_RANGE =
            Pattern.compile("\\b((?:19|20)\\d{2})\\s*[-–—]\\s*((?:19|20)\\d{2})\\b");

    private static final Pattern GRADUATION_YEAR =
            Pattern.compile(
                    "(?:окончил(?:а)?|выпуск|graduated|graduation)[^\\n]{0,30}?\\b((?:19|20)\\d{2})\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public EducationFacts extract(String text) {
        if (text == null || text.isBlank()) {
            return EducationFacts.empty();
        }

        String lower = text.toLowerCase(Locale.ROOT);
        String level = detectLevel(lower);
        String degree = detectDegree(lower);
        String specialty = detectSpecialty(text);
        Double years = detectYears(text);
        return new EducationFacts(level, degree, specialty, years);
    }

    public Integer graduationYear(String text) {
        Matcher matcher = GRADUATION_YEAR.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    public List<int[]> educationYearSpans(String text) {
        int[] block = EducationContext.educationBlock(text);
        if (block == null) {
            return List.of();
        }
        String value = text.substring(block[0], block[1]);
        List<int[]> spans = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\b(?:19|20)\\d{2}\\b").matcher(value);
        while (matcher.find()) {
            spans.add(new int[] {block[0] + matcher.start(), block[0] + matcher.end()});
        }
        return spans;
    }

    private String detectLevel(String lower) {
        if (lower.contains("высшее")) {
            return "HIGHER";
        }
        if (lower.contains("среднее специальное") || lower.contains("vocational")) {
            return "VOCATIONAL";
        }
        if (lower.contains("среднее")) {
            return "SECONDARY";
        }
        return null;
    }

    private String detectDegree(String lower) {
        if (lower.contains("бакалавр") || lower.contains("bachelor")) {
            return "BACHELOR";
        }
        if (lower.contains("магистр") || lower.contains("master")) {
            return "MASTER";
        }
        if (lower.contains("специалист") || lower.contains("specialist")) {
            return "SPECIALIST";
        }
        if (lower.contains("аспирант") || lower.contains("phd") || lower.contains("доктор")) {
            return "PHD";
        }
        return null;
    }

    private String detectSpecialty(String text) {
        Matcher matcher = SPECIALTY.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private Double detectYears(String text) {
        int[] block = EducationContext.educationBlock(text);
        if (block == null) {
            return null;
        }
        String value = text.substring(block[0], block[1]);
        Matcher matcher = YEAR_RANGE.matcher(value);
        List<int[]> intervals = new ArrayList<>();
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            int end = Integer.parseInt(matcher.group(2));
            if (end > start) {
                intervals.add(new int[] {start, end});
            }
        }
        if (intervals.isEmpty()) {
            return null;
        }
        intervals.sort(Comparator.comparingInt(interval -> interval[0]));
        int total = 0;
        int currentStart = intervals.getFirst()[0];
        int currentEnd = intervals.getFirst()[1];
        for (int index = 1; index < intervals.size(); index++) {
            int[] interval = intervals.get(index);
            if (interval[0] <= currentEnd) {
                currentEnd = Math.max(currentEnd, interval[1]);
            } else {
                total += currentEnd - currentStart;
                currentStart = interval[0];
                currentEnd = interval[1];
            }
        }
        total += currentEnd - currentStart;
        return (double) total;
    }
}
