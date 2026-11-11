package ats.resume.infrastructure.pii;

import ats.resume.domain.privacy.PiiDetector;
import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import com.google.i18n.phonenumbers.PhoneNumberMatch;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Структурированные PII: email, телефоны, ссылки, мессенджеры, документы и явные поля-метки.
 * Семантические сущности (имена, города, вузы) ищет локальная NER-модель, а не словарь.
 */
@Component
public class RegexPiiDetector implements PiiDetector {

    // UNICODE_CHARACTER_CLASS нужен, чтобы \b и \w работали с кириллицей.
    private static final int FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern EMAIL =
            Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", FLAGS);

    private static final Pattern PHONE_FALLBACK =
            Pattern.compile(
                    "(?<!\\d)(?:\\+7|8)[\\s(-]*\\d{3}[\\s)-]*\\d{3}[\\s-]*\\d{2}[\\s-]*\\d{2}(?!\\d)");

    private static final Pattern URL =
            Pattern.compile("\\b(?:https?://|www\\.)[^\\s<>\"']+", FLAGS);

    private static final Pattern GITHUB =
            Pattern.compile(
                    "\\b(?:https?://)?(?:www\\.)?github\\.com/[a-z0-9](?:[a-z0-9-]{0,38})"
                            + "(?:/[a-z0-9._-]+)?",
                    FLAGS);

    private static final Pattern LINKEDIN =
            Pattern.compile(
                    "\\b(?:https?://)?(?:www\\.)?linkedin\\.com/(?:in|pub)/[a-z0-9_%.-]+", FLAGS);

    private static final Pattern TELEGRAM =
            Pattern.compile(
                    "\\b(?:https?://)?(?:t\\.me|telegram\\.me)/[a-z0-9_]{4,32}\\b"
                            + "|\\b(?:telegram|телеграм|tg)\\s*[:\\-]?\\s*@[a-z0-9_]{4,32}\\b",
                    FLAGS);

    private static final Pattern PASSPORT =
            Pattern.compile(
                    "(?:паспорт|passport)[^\\n]{0,40}?\\b\\d{2}\\s?\\d{2}\\s?(?:№|no\\.?|N)?\\s?\\d{6}\\b",
                    FLAGS);

    private static final Pattern SNILS =
            Pattern.compile("(?<!\\d)\\d{3}-\\d{3}-\\d{3}\\s?\\d{2}(?!\\d)");

    private static final Pattern INN =
            Pattern.compile("(?:инн|inn)\\s*[:\\-]?\\s*\\d{10,12}\\b", FLAGS);

    private static final Pattern BIRTH_DATE =
            Pattern.compile(
                    "(?:дата\\s+рождения|д\\.\\s*р\\.|родил(?:ся|ась)|birth\\s*date|date\\s*of\\s*birth|dob)"
                            + "\\s*[:\\-]?\\s*(\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}|\\d{1,2}\\s+[а-яё]+\\s+\\d{4})",
                    FLAGS);

    private static final Pattern AGE_LABEL =
            Pattern.compile(
                    "(?:возраст|age)\\s*[:\\-]?\\s*(\\d{1,2})\\s*(?:лет|года|год|years?)?", FLAGS);

    private static final Pattern AGE_LINE =
            Pattern.compile("(?m)^\\s*(\\d{1,2})\\s*(?:лет|года|год)\\s*$", FLAGS);

    private static final Pattern GENDER =
            Pattern.compile(
                    "\\b(?:пол|gender)\\s*[:\\-]\\s*(мужской|женский|male|female|м|ж)\\b", FLAGS);

    private static final Pattern MARITAL_STATUS =
            Pattern.compile(
                    "(?:семейное\\s+положение|marital\\s+status)\\s*[:\\-]?\\s*([^\\n]{2,60})",
                    FLAGS);

    private static final Pattern ADDRESS =
            Pattern.compile(
                    "(?:адрес|address|проживает|прописан(?:а)?)\\s*[:\\-]\\s*([^\\n]{3,160})",
                    FLAGS);

    private static final Pattern PHOTO = Pattern.compile("\\b(?:фото|photo)\\b", FLAGS);

    private static final Pattern UNIVERSITY_FALLBACK =
            Pattern.compile(
                    "(?m)^[^\\n]*\\b(?:университет|институт|академия|колледж|university|institute|academy|college)\\b[^\\n]*$",
                    FLAGS);

    private static final Pattern GRADUATION_YEAR =
            Pattern.compile(
                    "(?:окончил(?:а)?|выпуск|graduated|graduation)[^\\n]{0,30}?\\b((?:19|20)\\d{2})\\b",
                    FLAGS);

    private static final Set<String> RESERVED_GITHUB_PATHS =
            Set.of(
                    "features",
                    "topics",
                    "about",
                    "pricing",
                    "login",
                    "join",
                    "marketplace",
                    "explore",
                    "settings",
                    "notifications",
                    "orgs",
                    "users",
                    "search",
                    "collections",
                    "trending",
                    "sponsors",
                    "readme");

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    @Override
    public List<PiiSpan> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<PiiSpan> spans = new ArrayList<>();
        addMatches(spans, EMAIL, text, PiiType.EMAIL, 0);
        addPhones(spans, text);
        addMatches(spans, GITHUB, text, PiiType.GITHUB, 0, this::isNotReservedGitHub);
        addMatches(spans, LINKEDIN, text, PiiType.LINKEDIN, 0);
        addMatches(spans, TELEGRAM, text, PiiType.TELEGRAM, 0);
        addMatches(spans, URL, text, PiiType.URL, 0);
        addMatches(spans, PASSPORT, text, PiiType.PASSPORT, 0);
        addMatches(spans, SNILS, text, PiiType.SNILS, 0);
        addMatches(spans, INN, text, PiiType.INN, 0);
        addMatches(spans, BIRTH_DATE, text, PiiType.BIRTH_DATE, 1);
        addMatches(spans, AGE_LABEL, text, PiiType.AGE, 1);
        addMatches(spans, AGE_LINE, text, PiiType.AGE, 1);
        addMatches(spans, GENDER, text, PiiType.GENDER, 1);
        addMatches(spans, MARITAL_STATUS, text, PiiType.MARITAL_STATUS, 1);
        addMatches(spans, ADDRESS, text, PiiType.LOCATION, 1);
        addMatches(spans, PHOTO, text, PiiType.PHOTO, 0);
        addMatches(spans, UNIVERSITY_FALLBACK, text, PiiType.UNIVERSITY, 0);
        addMatches(spans, GRADUATION_YEAR, text, PiiType.GRADUATION_YEAR, 1);
        return spans;
    }

    private void addPhones(List<PiiSpan> spans, String text) {
        for (PhoneNumberMatch match :
                phoneNumberUtil.findNumbers(
                        text, "RU", PhoneNumberUtil.Leniency.POSSIBLE, Long.MAX_VALUE)) {
            spans.add(
                    new PiiSpan(
                            match.start(), match.end(), PiiType.PHONE, 0.9, PiiSpan.Source.REGEX));
        }
        addMatches(spans, PHONE_FALLBACK, text, PiiType.PHONE, 0);
    }

    private void addMatches(
            List<PiiSpan> spans, Pattern pattern, String text, PiiType type, int group) {
        addMatches(spans, pattern, text, type, group, value -> true);
    }

    private void addMatches(
            List<PiiSpan> spans,
            Pattern pattern,
            String text,
            PiiType type,
            int group,
            java.util.function.Predicate<String> accept) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (!accept.test(matcher.group())) {
                continue;
            }
            int start = matcher.start(group);
            int end = matcher.end(group);
            if (start >= 0 && end > start) {
                spans.add(new PiiSpan(start, end, type, 0.9, PiiSpan.Source.REGEX));
            }
        }
    }

    private boolean isNotReservedGitHub(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        int index = lower.indexOf("github.com/");
        if (index < 0) {
            return false;
        }
        String tail = lower.substring(index + "github.com/".length());
        String path = tail.split("[/?#]", 2)[0];
        return !RESERVED_GITHUB_PATHS.contains(path);
    }
}
