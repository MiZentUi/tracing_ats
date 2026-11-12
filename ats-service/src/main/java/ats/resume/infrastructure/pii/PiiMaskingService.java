package ats.resume.infrastructure.pii;

import ats.resume.domain.privacy.EducationFacts;
import ats.resume.domain.privacy.MaskingResult;
import ats.resume.domain.privacy.PiiDetector;
import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import ats.resume.domain.vault.PiiPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Собирает PII-спаны всех слоёв, маскирует текст и готовит payload для шифрования. */
@Component
@RequiredArgsConstructor
public class PiiMaskingService {

    private final List<PiiDetector> detectors;
    private final PiiSpanResolver spanResolver;
    private final EducationExtractor educationExtractor;
    private final GitHubUrlExtractor githubUrlExtractor;

    public MaskingResult mask(String text, String originalFilename, String knownGithubUrl) {
        List<PiiSpan> spans = new ArrayList<>();
        for (PiiDetector detector : detectors) {
            spans.addAll(detector.detect(text));
        }

        String githubUrl = knownGithubUrl != null ? knownGithubUrl : githubUrlExtractor.find(text);
        addGitHubSpan(spans, text, githubUrl);

        List<PiiSpan> resolved = spanResolver.resolve(spans);
        Map<PiiType, Integer> counters = new EnumMap<>(PiiType.class);
        List<PiiPayload.PiiEntry> entries = new ArrayList<>();
        StringBuilder masked = new StringBuilder(text.length());
        int cursor = 0;

        for (PiiSpan span : resolved) {
            if (span.start() < cursor) {
                continue;
            }
            masked.append(text, cursor, span.start());
            int number = counters.merge(span.type(), 1, Integer::sum);
            String placeholder = "[PII_" + span.type().name() + "_" + number + "]";
            masked.append(placeholder);
            entries.add(
                    new PiiPayload.PiiEntry(
                            span.type(), placeholder, text.substring(span.start(), span.end())));
            cursor = span.end();
        }
        masked.append(text, cursor, text.length());

        EducationFacts education = educationExtractor.extract(text);
        String university =
                entries.stream()
                        .filter(entry -> entry.type() == PiiType.UNIVERSITY)
                        .map(PiiPayload.PiiEntry::value)
                        .findFirst()
                        .orElse(null);
        Integer graduationYear =
                entries.stream()
                        .filter(entry -> entry.type() == PiiType.GRADUATION_YEAR)
                        .map(PiiPayload.PiiEntry::value)
                        .map(PiiMaskingService::parseYear)
                        .filter(year -> year != null)
                        .max(Integer::compareTo)
                        .orElseGet(() -> educationGraduationYear(text));

        PiiPayload payload =
                new PiiPayload(entries, originalFilename, githubUrl, university, graduationYear);
        return new MaskingResult(masked.toString(), payload, education);
    }

    private void addGitHubSpan(List<PiiSpan> spans, String text, String githubUrl) {
        if (githubUrl == null) {
            return;
        }
        String username = githubUrlExtractor.username(githubUrl);
        String needle = username == null ? githubUrl : "github.com/" + username;
        int index = text.indexOf(needle);
        if (index < 0) {
            return;
        }
        spans.add(
                new PiiSpan(
                        index, index + needle.length(), PiiType.GITHUB, 1.0, PiiSpan.Source.REGEX));
    }

    /**
     * Если год попал внутрь более широкого спана (например, всей строки вуза), отдельной записи
     * GRADUATION_YEAR может не быть. Тогда берём максимум годов из блока образования.
     */
    private Integer educationGraduationYear(String text) {
        Integer explicit = educationExtractor.graduationYear(text);
        if (explicit != null) {
            return explicit;
        }
        return educationExtractor.educationYearSpans(text).stream()
                .map(span -> Integer.parseInt(text.substring(span[0], span[1])))
                .max(Integer::compareTo)
                .orElse(null);
    }

    private static Integer parseYear(String value) {
        if (value == null || value.length() != 4) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
