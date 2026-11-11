package ats.vacancy.screening;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Детерминированное решение по шорт-листу. Кандидат проходит, если его балл не ниже порога или он
 * входит в top-N. Пересечение вилки с порогом даёт borderline; GitHub для borderline не зовём,
 * потому что внешние источники разрешены только шорт-листу.
 */
@Component
@RequiredArgsConstructor
public class ScreeningPolicy {

    private final ScreeningPolicyProperties properties;

    public List<DecisionResult> decide(List<CandidateScore> candidates) {
        List<CandidateScore> sorted = new ArrayList<>(candidates);
        sorted.sort(
                Comparator.comparingDouble(CandidateScore::score)
                        .reversed()
                        .thenComparing(CandidateScore::applicationId));

        List<DecisionResult> results = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            CandidateScore candidate = sorted.get(index);
            boolean inTopN = index < properties.getTopN();
            Decision decision;
            if (candidate.score() >= properties.getThreshold() || inTopN) {
                decision = Decision.SHORTLISTED;
            } else if (candidate.scoreHigh() >= properties.getThreshold()) {
                decision = Decision.BORDERLINE;
            } else if (candidate.scoreHigh()
                    >= properties.getThreshold() - properties.getBorderlineMargin()) {
                decision = Decision.BORDERLINE;
            } else {
                decision = Decision.NOT_SHORTLISTED;
            }
            results.add(
                    new DecisionResult(
                            candidate.applicationId(), decision, decision == Decision.SHORTLISTED));
        }
        return results;
    }

    public enum Decision {
        SHORTLISTED,
        BORDERLINE,
        NOT_SHORTLISTED
    }

    public record CandidateScore(
            String applicationId, double score, double scoreLow, double scoreHigh) {}

    public record DecisionResult(String applicationId, Decision decision, boolean enrich) {}
}
