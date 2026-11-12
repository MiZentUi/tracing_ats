package ats.vacancy.screening;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScreeningPolicyTest {

    @Test
    void appliesThresholdTopNAndBorderline() {
        ScreeningPolicyProperties properties = new ScreeningPolicyProperties();
        properties.setThreshold(50);
        properties.setTopN(2);
        properties.setBorderlineMargin(5);
        ScreeningPolicy policy = new ScreeningPolicy(properties);

        List<ScreeningPolicy.DecisionResult> results =
                policy.decide(
                        List.of(
                                candidate("a", 90, 80, 95),
                                candidate("b", 80, 70, 90),
                                candidate("c", 40, 35, 52),
                                candidate("d", 30, 25, 35)));

        assertThat(decision(results, "a")).isEqualTo(ScreeningPolicy.Decision.SHORTLISTED);
        assertThat(decision(results, "b")).isEqualTo(ScreeningPolicy.Decision.SHORTLISTED);
        assertThat(decision(results, "c")).isEqualTo(ScreeningPolicy.Decision.BORDERLINE);
        assertThat(decision(results, "d")).isEqualTo(ScreeningPolicy.Decision.NOT_SHORTLISTED);
    }

    @Test
    void topNShortlistsEvenBelowThreshold() {
        ScreeningPolicyProperties properties = new ScreeningPolicyProperties();
        properties.setThreshold(90);
        properties.setTopN(1);
        properties.setBorderlineMargin(5);
        ScreeningPolicy policy = new ScreeningPolicy(properties);

        List<ScreeningPolicy.DecisionResult> results =
                policy.decide(
                        List.of(candidate("best", 60, 50, 70), candidate("second", 55, 50, 60)));

        assertThat(decision(results, "best")).isEqualTo(ScreeningPolicy.Decision.SHORTLISTED);
        assertThat(decision(results, "second")).isEqualTo(ScreeningPolicy.Decision.NOT_SHORTLISTED);
    }

    private ScreeningPolicy.CandidateScore candidate(
            String id, double score, double low, double high) {
        return new ScreeningPolicy.CandidateScore(id, score, low, high);
    }

    private ScreeningPolicy.Decision decision(
            List<ScreeningPolicy.DecisionResult> results, String id) {
        return results.stream()
                .filter(result -> result.applicationId().equals(id))
                .findFirst()
                .orElseThrow()
                .decision();
    }
}
