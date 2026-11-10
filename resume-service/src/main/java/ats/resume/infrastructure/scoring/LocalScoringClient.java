package ats.resume.infrastructure.scoring;

import ats.resume.domain.scoring.EvidenceValidator;
import ats.resume.domain.scoring.ScoreCalculator;
import ats.resume.domain.scoring.ScoringClient;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.VacancyTemplate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Локальный детерминированный адаптер скоринга для MVP. Он не вызывает LLM и не выдаёт себя за
 * scoring-service: категории m/t здесь эвристические, чтобы сквозной сценарий работал без
 * Python-сервиса. При появлении настоящего контракта заменяется gRPC-адаптером.
 */
@Component
public class LocalScoringClient implements ScoringClient {

    private static final Pattern YEARS =
            Pattern.compile(
                    "(\\d{1,2})\\s*(?:\\+)?\\s*(?:лет|года|год|years?)", Pattern.CASE_INSENSITIVE);

    private static final Pattern YEAR = Pattern.compile("\\b((?:19|20)\\d{2})\\b");

    private static final Pattern YEAR_RANGE =
            Pattern.compile("\\b((?:19|20)\\d{2})\\s*[-–—]\\s*((?:19|20)\\d{2})\\b");

    private static final List<String> ACTION_VERBS =
            List.of(
                    "разработал",
                    "разработала",
                    "внедрил",
                    "внедрила",
                    "спроектировал",
                    "спроектировала",
                    "оптимизировал",
                    "оптимизировала",
                    "ускорил",
                    "ускорила",
                    "сократил",
                    "сократила",
                    "построил",
                    "построила",
                    "реализовал",
                    "реализовала",
                    "implemented",
                    "designed",
                    "built",
                    "improved",
                    "optimized",
                    "migrated",
                    "led");

    @Override
    public String version() {
        return "local-heuristic-v1";
    }

    @Override
    public ScoringResult score(ScoringRequest request) {
        VacancyTemplate template = request.template();
        int currentYear = request.now().atZone(java.time.ZoneOffset.UTC).getYear();
        List<ScoreCalculator.EvaluatedRequirement> evaluated = new ArrayList<>();
        Set<String> flags = new LinkedHashSet<>();

        for (Requirement requirement : template.requirements()) {
            Optional<Match> match = findSkill(request.maskedText(), requirement.skill());
            ScoreCalculator.EvaluatedRequirement item;
            if (match.isPresent()) {
                Match value = match.get();
                double t = estimateStrength(value.sentence());
                double rho =
                        ScoreCalculator.rho(
                                value.lastUsedYear(), currentYear, requirement.halfLifeYears());
                String quote =
                        EvidenceValidator.validate(value.sentence(), request.maskedText())
                                .orElse(null);
                item =
                        new ScoreCalculator.EvaluatedRequirement(
                                requirement,
                                1.0,
                                t,
                                rho,
                                externalQ(request.externalEvidence(), requirement.skill()),
                                value.yearsUsed(),
                                quote);
            } else {
                item =
                        new ScoreCalculator.EvaluatedRequirement(
                                requirement,
                                0,
                                0,
                                1,
                                externalQ(request.externalEvidence(), requirement.skill()),
                                0,
                                null);
            }
            evaluated.add(item);
        }

        request.externalEvidence().values().forEach(evidence -> flags.addAll(evidence.flags()));

        double f = fit(template, evaluated);
        ScoreCalculator.Evaluation evaluation = ScoreCalculator.evaluate(evaluated, template, f);
        flags.addAll(evaluation.flags());
        return new ScoringResult(
                evaluation.score(),
                evaluation.scoreLow(),
                evaluation.scoreHigh(),
                evaluation.capApplied(),
                List.copyOf(flags),
                evaluation.requirements());
    }

    private Optional<Match> findSkill(String text, String skill) {
        if (text == null || text.isBlank() || skill == null || skill.isBlank()) {
            return Optional.empty();
        }
        int index = indexOfSkill(text, skill);
        if (index < 0) {
            return Optional.empty();
        }
        int[] bounds = sentenceBounds(text, index);
        int[] line = lineBounds(text, index);
        String sentence = text.substring(bounds[0], bounds[1]).strip();
        String context = text.substring(line[0], line[1]);
        return Optional.of(new Match(sentence, lastUsedYear(context), yearsUsed(context)));
    }

    private int indexOfSkill(String text, String skill) {
        String value = skill.strip();
        if (value.isEmpty()) {
            return -1;
        }
        if (value.matches("[\\p{L}\\p{N} .+#-]+")) {
            Pattern pattern =
                    Pattern.compile(
                            "(?iu)(?<![\\p{L}\\p{N}])"
                                    + Pattern.quote(value)
                                    + "(?![\\p{L}\\p{N}])");
            Matcher matcher = pattern.matcher(text);
            return matcher.find() ? matcher.start() : -1;
        }
        return text.toLowerCase(Locale.ROOT).indexOf(value.toLowerCase(Locale.ROOT));
    }

    private int[] sentenceBounds(String text, int index) {
        int start = index;
        while (start > 0 && !isBoundary(text.charAt(start - 1))) {
            start--;
        }
        int end = index;
        while (end < text.length() && !isBoundary(text.charAt(end))) {
            end++;
        }
        if (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return new int[] {start, end};
    }

    private boolean isBoundary(char value) {
        return value == '.' || value == '!' || value == '?' || value == '\n';
    }

    private int[] lineBounds(String text, int index) {
        int start = index;
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        int end = index;
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        return new int[] {start, end};
    }

    private double estimateStrength(String sentence) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        if (sentence.chars().anyMatch(Character::isDigit)) {
            return 0.8;
        }
        if (ACTION_VERBS.stream().anyMatch(lower::contains)) {
            return 0.8;
        }
        if (sentence.length() <= 40) {
            return 0.3;
        }
        return 0.5;
    }

    private Integer lastUsedYear(String sentence) {
        Matcher matcher = YEAR.matcher(sentence);
        Integer result = null;
        while (matcher.find()) {
            result = Integer.parseInt(matcher.group(1));
        }
        return result;
    }

    private double yearsUsed(String context) {
        Matcher matcher = YEARS.matcher(context);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        Matcher range = YEAR_RANGE.matcher(context);
        double result = 0;
        while (range.find()) {
            int start = Integer.parseInt(range.group(1));
            int end = Integer.parseInt(range.group(2));
            if (end >= start) {
                result = Math.max(result, end - start);
            }
        }
        return result;
    }

    private double externalQ(
            Map<String, ScoringRequest.ExternalSkillEvidence> evidence, String skill) {
        if (skill == null) {
            return 0;
        }
        ScoringRequest.ExternalSkillEvidence value =
                evidence.get(skill.toLowerCase(Locale.ROOT).strip());
        return value == null ? 0 : value.q();
    }

    private double fit(
            VacancyTemplate template, List<ScoreCalculator.EvaluatedRequirement> requirements) {
        double maxRequired = 0;
        double used = 0;
        for (ScoreCalculator.EvaluatedRequirement item : requirements) {
            maxRequired = Math.max(maxRequired, item.requirement().yearsRequired());
            used = Math.max(used, item.yearsUsed());
        }
        double tenure = maxRequired <= 0 ? 0.7 : Math.min(1, used / maxRequired);
        double level = 0.7;
        double domain = 0.7;
        return (level + domain + tenure) / 3;
    }

    private record Match(String sentence, Integer lastUsedYear, double yearsUsed) {}
}
