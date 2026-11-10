package ats.resume.infrastructure.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.domain.privacy.EducationFacts;
import ats.resume.domain.scoring.ScoringRequest;
import ats.resume.domain.scoring.ScoringResult;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.domain.vacancy.VacancyTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalScoringClientTest {

    private final LocalScoringClient client = new LocalScoringClient();
    private final VacancyTemplate template = template();

    @Test
    void isDeterministicAndKeepsQuoteAsSubstring() {
        String text = "Java developer with 3 years of experience. Spring Boot, PostgreSQL.";
        ScoringRequest request =
                new ScoringRequest(
                        UUID.randomUUID(), text, EducationFacts.empty(), template, Map.of(), now());

        ScoringResult first = client.score(request);
        ScoringResult second = client.score(request);

        assertThat(first.score()).isEqualTo(second.score());
        assertThat(first.scoreLow()).isLessThanOrEqualTo(first.score());
        assertThat(first.scoreHigh()).isGreaterThanOrEqualTo(first.score());
        first.requirements()
                .forEach(
                        requirement -> {
                            if (requirement.evidenceQuote() != null) {
                                assertThat(text).contains(requirement.evidenceQuote());
                            }
                        });
        assertThat(first.requirements())
                .anyMatch(
                        requirement ->
                                requirement
                                                .requirementId()
                                                .equals(requirement("Java").id().toString())
                                        && requirement.cR() > 0);
    }

    @Test
    void usesYearRangeOnTheSameLineForTenure() {
        String text =
                """
                2021-2026 Java-разработчик. Java, Spring Boot.
                """;
        ScoringRequest request =
                new ScoringRequest(
                        UUID.randomUUID(), text, EducationFacts.empty(), template, Map.of(), now());

        ScoringResult result = client.score(request);

        assertThat(result.requirements())
                .filteredOn(requirement -> requirement.text().equals("Java"))
                .singleElement()
                .satisfies(requirement -> assertThat(requirement.cR()).isGreaterThan(0));
    }

    @Test
    void externalEvidenceIncreasesScoreAndNeverPenalizesAbsence() {
        String text = "Java developer with 3 years of experience.";
        ScoringRequest without =
                new ScoringRequest(
                        UUID.randomUUID(), text, EducationFacts.empty(), template, Map.of(), now());
        ScoringRequest with =
                without.withExternalEvidence(
                        Map.of(
                                "java",
                                new ScoringRequest.ExternalSkillEvidence(
                                        "java", 0.8, Map.of("repositories", "2"), List.of())));

        assertThat(client.score(with).score())
                .isGreaterThanOrEqualTo(client.score(without).score());
    }

    private Requirement requirement(String skill) {
        return template.requirements().stream()
                .filter(requirement -> requirement.skill().equals(skill))
                .findFirst()
                .orElseThrow();
    }

    private VacancyTemplate template() {
        Requirement java =
                new Requirement(
                        UUID.randomUUID(), "Java", "Java", RequirementKind.MUST, 3, true, 3, 5);
        Requirement kafka =
                new Requirement(
                        UUID.randomUUID(), "Kafka", "Kafka", RequirementKind.NICE, 1, false, 1, 3);
        return new VacancyTemplate(
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
                List.of(java, kafka));
    }

    private Instant now() {
        return Instant.parse("2026-11-10T00:00:00Z");
    }
}
