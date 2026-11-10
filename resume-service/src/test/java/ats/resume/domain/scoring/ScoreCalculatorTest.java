package ats.resume.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.domain.vacancy.VacancyTemplate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    @Test
    void computesQEDAndRho() {
        assertThat(ScoreCalculator.q(1, 0.5, 0.9)).isEqualTo(0.45);
        assertThat(ScoreCalculator.e(0.8, 0.5, 1)).isEqualTo(0.9);
        assertThat(ScoreCalculator.d(5, 3)).isEqualTo(1);
        assertThat(ScoreCalculator.d(1.5, 3)).isEqualTo(0.5);
        assertThat(ScoreCalculator.d(0, 0)).isEqualTo(1);
        assertThat(ScoreCalculator.rho(2023, 2026, 3)).isEqualTo(0.5);
        assertThat(ScoreCalculator.rho(null, 2026, 3)).isEqualTo(1);
    }

    @Test
    void unionsTenurePeriodsWithoutDoubleCounting() {
        double years =
                TenureCalculator.years(
                        List.of(
                                new TenureCalculator.Period(2018, 2021),
                                new TenureCalculator.Period(2020, 2023)));
        assertThat(years).isEqualTo(5);
    }

    @Test
    void capsScoreWhenCriticalRequirementIsUncovered() {
        Requirement critical =
                new Requirement(
                        UUID.randomUUID(), "Java", "Java", RequirementKind.MUST, 3, true, 3, 5);
        VacancyTemplate template =
                new VacancyTemplate(
                        UUID.randomUUID(),
                        1,
                        "Java",
                        "java",
                        "middle",
                        1,
                        0.7,
                        0.15,
                        0.15,
                        "prompt-v1",
                        "scoring-v1",
                        "v1",
                        List.of(critical));

        ScoreCalculator.Evaluation result =
                ScoreCalculator.evaluate(
                        List.of(
                                new ScoreCalculator.EvaluatedRequirement(
                                        critical, 0, 0, 1, 0, 0, null)),
                        template,
                        1);

        assertThat(result.capApplied()).isTrue();
        assertThat(result.score()).isLessThanOrEqualTo(49);
        assertThat(result.flags()).contains("CRITICAL_REQUIREMENT_UNCOVERED");
        assertThat(result.scoreLow()).isLessThanOrEqualTo(result.score());
        assertThat(result.scoreHigh()).isGreaterThanOrEqualTo(result.score());
    }

    @Test
    void externalEvidenceNeverLowersScore() {
        Requirement java =
                new Requirement(
                        UUID.randomUUID(), "Java", "Java", RequirementKind.MUST, 3, false, 3, 5);
        VacancyTemplate template =
                new VacancyTemplate(
                        UUID.randomUUID(),
                        1,
                        "Java",
                        "java",
                        "middle",
                        1,
                        0.7,
                        0.15,
                        0.15,
                        "prompt-v1",
                        "scoring-v1",
                        "v1",
                        List.of(java));

        ScoreCalculator.Evaluation without =
                ScoreCalculator.evaluate(
                        List.of(
                                new ScoreCalculator.EvaluatedRequirement(
                                        java, 1, 0.5, 1, 0, 3, "Java")),
                        template,
                        0.8);
        ScoreCalculator.Evaluation with =
                ScoreCalculator.evaluate(
                        List.of(
                                new ScoreCalculator.EvaluatedRequirement(
                                        java, 1, 0.5, 1, 0.8, 3, "Java")),
                        template,
                        0.8);

        assertThat(with.score()).isGreaterThanOrEqualTo(without.score());
    }
}
