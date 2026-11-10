package ats.resume.domain.scoring;

import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.domain.vacancy.VacancyTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Детерминированная арифметика модели скоринга. Временный локальный порт scoring-service: формулы
 * повторяют README и будут удалены, когда появится настоящий gRPC-контракт Python-сервиса. Внутри
 * нет времени и случайности — «сегодня» приходит параметром.
 */
public final class ScoreCalculator {

    public static final double CAP_THRESHOLD = 0.2;
    public static final double CAP_SCORE = 49;

    private ScoreCalculator() {}

    public static double q(double m, double t, double rho) {
        return clamp01(m) * clamp01(t) * clamp01(rho);
    }

    public static double rho(Integer lastUsedYear, int currentYear, double halfLifeYears) {
        if (lastUsedYear == null || halfLifeYears <= 0) {
            return 1;
        }
        double years = Math.max(0, currentYear - lastUsedYear);
        return Math.pow(0.5, years / halfLifeYears);
    }

    public static double externalConfidence(List<Double> qValues) {
        double remaining = 1;
        for (double value : qValues) {
            remaining *= 1 - clamp01(value);
        }
        return clamp01(1 - remaining);
    }

    public static double e(double qText, double qExt, double lambda) {
        return clamp01(1 - (1 - clamp01(qText)) * (1 - clamp01(lambda) * clamp01(qExt)));
    }

    public static double d(double yearsUsed, double yearsRequired) {
        if (yearsRequired <= 0) {
            return 1;
        }
        return clamp01(Math.max(0, yearsUsed) / yearsRequired);
    }

    public static double cR(double e, double d) {
        return clamp01(e) * clamp01(d);
    }

    public static Evaluation evaluate(
            List<EvaluatedRequirement> requirements, VacancyTemplate template, double f) {
        if (requirements.isEmpty()) {
            return new Evaluation(0, 0, 0, false, List.of(), List.of());
        }

        List<ScoredRequirement> normal = score(requirements, template, Mode.NORMAL);
        List<ScoredRequirement> low = score(requirements, template, Mode.LOW);
        List<ScoredRequirement> high = score(requirements, template, Mode.HIGH);

        double score = aggregate(normal, template, f);
        double scoreLow = Math.min(score, aggregate(low, template, f));
        double scoreHigh = Math.max(score, aggregate(high, template, f));

        boolean capApplied =
                normal.stream()
                        .anyMatch(
                                item -> item.requirement().critical() && item.cR() < CAP_THRESHOLD);
        List<String> flags = new ArrayList<>();
        if (capApplied) {
            score = Math.min(score, CAP_SCORE);
            scoreLow = Math.min(scoreLow, CAP_SCORE);
            scoreHigh = Math.min(scoreHigh, CAP_SCORE);
            flags.add("CRITICAL_REQUIREMENT_UNCOVERED");
        }

        double roundedScore = round2(score);
        double roundedLow = Math.min(round2(scoreLow), roundedScore);
        double roundedHigh = Math.max(round2(scoreHigh), roundedScore);

        List<ScoringResult.RequirementResult> results =
                normal.stream().map(ScoreCalculator::toResult).toList();
        return new Evaluation(
                roundedScore, roundedLow, roundedHigh, capApplied, List.copyOf(flags), results);
    }

    private static List<ScoredRequirement> score(
            List<EvaluatedRequirement> requirements, VacancyTemplate template, Mode mode) {
        List<ScoredRequirement> result = new ArrayList<>(requirements.size());
        for (EvaluatedRequirement item : requirements) {
            double t = mode == Mode.HIGH ? 1 : item.t();
            double qText = q(item.m(), t, item.rho());
            if (mode == Mode.LOW && t < 0.8 && item.qExt() <= 0) {
                qText = 0;
            }
            double e = e(qText, item.qExt(), template.githubWeight());
            double d = d(item.yearsUsed(), item.requirement().yearsRequired());
            result.add(new ScoredRequirement(item, qText, e, d, cR(e, d)));
        }
        return result;
    }

    private static double aggregate(
            List<ScoredRequirement> requirements, VacancyTemplate template, double f) {
        double must = weightedMean(requirements, true);
        double nice = weightedMean(requirements, false);
        double score =
                100 * (template.wM() * must + template.wN() * nice + template.wF() * clamp01(f));
        return clamp(score, 0, 100);
    }

    private static double weightedMean(List<ScoredRequirement> requirements, boolean must) {
        double weighted = 0;
        double totalWeight = 0;
        for (ScoredRequirement item : requirements) {
            boolean isMust = item.requirement().kind() == RequirementKind.MUST;
            if (isMust != must) {
                continue;
            }
            int weight = Math.max(1, item.requirement().weight());
            weighted += weight * item.cR();
            totalWeight += weight;
        }
        return totalWeight == 0 ? 1 : weighted / totalWeight;
    }

    private static ScoringResult.RequirementResult toResult(ScoredRequirement scored) {
        EvaluatedRequirement item = scored.evaluated();
        Requirement requirement = item.requirement();
        ScoringResult.RequirementResult.Status status =
                scored.cR() >= 0.8
                        ? ScoringResult.RequirementResult.Status.COVERED
                        : scored.cR() >= 0.4
                                ? ScoringResult.RequirementResult.Status.PARTIAL
                                : ScoringResult.RequirementResult.Status.GAP;
        ScoringResult.RequirementResult.EvidenceSource source =
                item.quote() != null
                        ? ScoringResult.RequirementResult.EvidenceSource.RESUME
                        : item.qExt() > 0
                                ? ScoringResult.RequirementResult.EvidenceSource.GITHUB
                                : null;
        String gap =
                status == ScoringResult.RequirementResult.Status.COVERED
                        ? null
                        : item.quote() == null && item.qExt() <= 0
                                ? "Навык не найден в обезличенном тексте"
                                : "Недостаточно подтверждений";
        return new ScoringResult.RequirementResult(
                requirement.id().toString(),
                requirement.text(),
                requirement.kind(),
                requirement.weight(),
                requirement.critical(),
                round3(item.m()),
                round3(item.t()),
                round3(item.rho()),
                round3(scored.cR()),
                status,
                source,
                Optional.ofNullable(item.quote()).orElse(null),
                gap,
                item.qExt() > 0);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round3(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static double clamp01(double value) {
        return clamp(value, 0, 1);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record EvaluatedRequirement(
            Requirement requirement,
            double m,
            double t,
            double rho,
            double qExt,
            double yearsUsed,
            String quote) {}

    public record Evaluation(
            double score,
            double scoreLow,
            double scoreHigh,
            boolean capApplied,
            List<String> flags,
            List<ScoringResult.RequirementResult> requirements) {}

    private record ScoredRequirement(
            EvaluatedRequirement evaluated, double qText, double e, double d, double cR) {

        private Requirement requirement() {
            return evaluated.requirement();
        }
    }

    private enum Mode {
        NORMAL,
        LOW,
        HIGH
    }
}
