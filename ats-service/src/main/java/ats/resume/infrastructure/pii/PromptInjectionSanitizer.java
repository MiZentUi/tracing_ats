package ats.resume.infrastructure.pii;

import ats.resume.domain.privacy.InjectionFlag;
import ats.resume.domain.privacy.SanitizedText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Детерминированный санитайзер prompt injection. Это defense-in-depth: основная защита — промпт
 * scoring-service, где резюме передаётся как данные, и строгая схема ответа. Правила намеренно
 * контекстные, чтобы не удалять обычный текст вроде «инструкция по охране труда».
 */
@Component
public class PromptInjectionSanitizer {

    public static final String REPLACEMENT = "[REMOVED_INSTRUCTION]";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Rule> RULES =
            List.of(
                    rule(
                            "ignore_previous",
                            "\\bignore\\s+(?:all\\s+)?(?:previous|prior|above)\\s+instructions?\\b"),
                    rule(
                            "disregard_previous",
                            "\\bdisregard\\s+(?:all\\s+)?(?:previous|prior|above)\\s+instructions?\\b"),
                    rule(
                            "forget_previous",
                            "\\bforget\\s+(?:all\\s+)?(?:previous|prior|above)\\s+instructions?\\b"),
                    rule("system_prompt", "\\b(?:system|developer)\\s+prompt\\b"),
                    rule(
                            "you_are_now",
                            "\\byou\\s+are\\s+now\\s+(?:a|an|the)?\\s*(?:ai|assistant|model|system|developer|hacker)\\b"),
                    rule(
                            "act_as",
                            "\\bact\\s+as\\s+(?:a|an|the)?\\s*(?:ai|assistant|model|system|developer)\\b"),
                    rule(
                            "return_score",
                            "\\breturn\\s+(?:the\\s+)?(?:score|rating|json|number|percentage)\\b"),
                    rule(
                            "output_json",
                            "\\boutput\\s+(?:only\\s+)?(?:json|score|rating|number)\\b"),
                    rule(
                            "assign_score",
                            "\\b(?:set|assign|give)\\s+(?:me\\s+)?(?:a\\s+)?(?:score|rating)\\s*(?:of|=|:)?\\s*\\d{1,3}\\b"),
                    rule("jailbreak", "\\b(?:jailbreak|dan\\s+mode|developer\\s+mode)\\b"),
                    rule(
                            "ru_ignore_previous",
                            "игнорируй(?:те)?\\s+(?:все\\s+)?(?:предыдущие|прежние|вышеуказанные)\\s+инструкции"),
                    rule(
                            "ru_forget_previous",
                            "забудь(?:те)?\\s+(?:все\\s+)?(?:предыдущие|прежние)\\s+инструкции"),
                    rule(
                            "ru_you_are_now",
                            "ты\\s+теперь\\s+(?:ai|ассистент|модель|система|разработчик)"),
                    rule(
                            "ru_act_as",
                            "действуй(?:те)?\\s+как\\s+(?:ai|ассистент|модель|система|разработчик)"),
                    rule("ru_return_score", "верни(?:те)?\\s+(?:оценку|балл|скор|json|рейтинг)"),
                    rule(
                            "ru_assign_score",
                            "поставь(?:те)?\\s+(?:мне\\s+)?(?:оценку|балл|скор)\\s*\\d{0,3}"),
                    rule("ru_output_json", "выведи(?:те)?\\s+(?:только\\s+)?(?:json|оценку|балл)"),
                    rule("ru_system_prompt", "системный\\s+промпт"),
                    rule(
                            "override_safety",
                            "\\b(?:override|bypass)\\s+(?:the\\s+)?(?:safety|filter|policy|instructions?)\\b"),
                    rule(
                            "do_not_follow",
                            "\\bdo\\s+not\\s+follow\\s+(?:the\\s+)?(?:instructions?|rules?)\\b"),
                    rule("prompt_injection", "\\bprompt\\s+injection\\b"),
                    rule(
                            "reveal_prompt",
                            "\\b(?:return|print|reveal)\\s+(?:the\\s+)?(?:system|hidden)\\s+(?:prompt|instructions?)\\b"),
                    rule(
                            "boost_score",
                            "\\b(?:increase|boost|maximize)\\s+(?:my|the)\\s+(?:score|rating)\\b"),
                    rule("ru_do_not_follow", "не\\s+следуй\\s+(?:инструкциям|правилам)"),
                    rule("ru_reveal_prompt", "раскрой\\s+(?:системный\\s+)?промпт"),
                    rule("ru_boost_score", "повысь\\s+(?:мне\\s+)?(?:оценку|балл|скор)"),
                    new Rule(
                            "role_line",
                            Pattern.compile(
                                    "(?im)^\\s*(?:system|assistant|developer|user)\\s*:.*$",
                                    FLAGS)),
                    new Rule(
                            "prompt_section",
                            Pattern.compile(
                                    "(?im)^\\s*(?:###|---)\\s*(?:system|instruction|prompt)\\b.*$",
                                    FLAGS)));

    public SanitizedText sanitize(String text) {
        if (text == null || text.isBlank()) {
            return new SanitizedText(text == null ? "" : text, List.of());
        }

        List<Span> spans = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                spans.add(new Span(rule.id(), matcher.start(), matcher.end()));
            }
        }
        if (spans.isEmpty()) {
            return new SanitizedText(text, List.of());
        }

        spans.sort(Comparator.comparingInt(Span::start).thenComparingInt(Span::end));
        List<Span> merged = new ArrayList<>();
        Span current = spans.getFirst();
        for (int index = 1; index < spans.size(); index++) {
            Span next = spans.get(index);
            if (next.start() <= current.end()) {
                current =
                        new Span(
                                current.rule(),
                                current.start(),
                                Math.max(current.end(), next.end()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        StringBuilder sanitized = new StringBuilder(text.length());
        List<InjectionFlag> flags = new ArrayList<>();
        int cursor = 0;
        for (Span span : merged) {
            sanitized.append(text, cursor, span.start());
            sanitized.append(REPLACEMENT);
            String fragment = text.substring(span.start(), span.end());
            flags.add(
                    new InjectionFlag(
                            span.rule(), lineNumber(text, span.start()), sha256(fragment)));
            cursor = span.end();
        }
        sanitized.append(text, cursor, text.length());
        return new SanitizedText(sanitized.toString(), flags);
    }

    private int lineNumber(String text, int index) {
        int line = 1;
        for (int position = 0; position < index; position++) {
            if (text.charAt(position) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Rule rule(String id, String regex) {
        return new Rule(id, Pattern.compile(regex, FLAGS));
    }

    private record Rule(String id, Pattern pattern) {}

    private record Span(String rule, int start, int end) {}
}
