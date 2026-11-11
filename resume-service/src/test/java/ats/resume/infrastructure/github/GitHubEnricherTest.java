package ats.resume.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.domain.enrichment.GitHubClient;
import ats.resume.domain.enrichment.GitHubProfile;
import ats.resume.domain.scoring.ExternalSkillEvidence;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.domain.vacancy.RequirementKind;
import ats.resume.infrastructure.pii.GitHubUrlExtractor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitHubEnricherTest {

    @Test
    void computesEvidenceForMatchingSkillOnly() {
        GitHubClient client =
                username ->
                        new GitHubProfile(
                                username,
                                List.of(
                                        new GitHubProfile.GitHubRepository(
                                                "backend",
                                                false,
                                                "Java",
                                                Map.of("Java", 120_000L),
                                                120,
                                                Instant.parse("2025-01-01T00:00:00Z"),
                                                Instant.parse("2026-10-01T00:00:00Z"),
                                                true,
                                                true,
                                                true,
                                                "Spring Boot service",
                                                List.of("spring-boot"),
                                                "Java service with tests")),
                                List.of());
        GitHubEnricher enricher = new GitHubEnricher(client, new GitHubUrlExtractor());

        List<ExternalSkillEvidence> evidence =
                enricher.enrich(
                        "https://github.com/example",
                        List.of(
                                new Requirement(
                                        UUID.randomUUID(),
                                        "Java",
                                        "Java",
                                        RequirementKind.MUST,
                                        3,
                                        true,
                                        3,
                                        5),
                                new Requirement(
                                        UUID.randomUUID(),
                                        "Kafka",
                                        "Kafka",
                                        RequirementKind.NICE,
                                        1,
                                        false,
                                        1,
                                        3)),
                        Instant.parse("2026-11-10T00:00:00Z"));

        assertThat(evidence).hasSize(1);
        assertThat(evidence.getFirst().skill()).isEqualTo("java");
        assertThat(evidence.getFirst().q()).isBetween(0.0, 1.0);
        assertThat(evidence.getFirst().facts()).containsKey("codeLines");
    }

    @Test
    void noGithubMeansNoEvidenceAndNoPenalty() {
        GitHubEnricher enricher = new GitHubEnricher(username -> null, new GitHubUrlExtractor());
        assertThat(
                        enricher.enrich(
                                null,
                                List.of(
                                        new Requirement(
                                                UUID.randomUUID(),
                                                "Java",
                                                "Java",
                                                RequirementKind.MUST,
                                                3,
                                                true,
                                                3,
                                                5)),
                                Instant.now()))
                .isEmpty();
    }
}
