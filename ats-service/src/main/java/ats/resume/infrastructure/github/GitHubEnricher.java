package ats.resume.infrastructure.github;

import ats.resume.domain.enrichment.GitHubClient;
import ats.resume.domain.enrichment.GitHubProfile;
import ats.resume.domain.scoring.ExternalSkillEvidence;
import ats.resume.domain.vacancy.Requirement;
import ats.resume.infrastructure.pii.GitHubUrlExtractor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Считает q_gh по конкретному навыку, а не «общий балл GitHub». Авторство A для собственных
 * не-форков принимается за 1: git blame в MVP не вызывается. Отсутствие репозиториев не штрафует.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubEnricher {

    private static final double L_REF = 3000;
    private static final double BYTES_PER_LINE = 40;

    private final GitHubClient gitHubClient;
    private final GitHubUrlExtractor githubUrlExtractor;

    public List<ExternalSkillEvidence> enrich(
            String githubUrl, List<Requirement> requirements, Instant now) {
        String username = githubUrlExtractor.username(githubUrl);
        if (username == null) {
            return List.of();
        }
        GitHubProfile profile;
        try {
            profile = gitHubClient.fetch(username);
        } catch (RuntimeException e) {
            // Внешний источник не должен ронять пакет и не должен уменьшать балл.
            log.warn("github_enrichment_skipped reason=source_unavailable");
            return List.of();
        }
        if (profile == null || profile.repositories().isEmpty()) {
            return List.of();
        }

        List<ExternalSkillEvidence> evidence = new ArrayList<>();
        for (Requirement requirement : requirements) {
            if (requirement.educationLevel() != null
                    || requirement.skill() == null
                    || requirement.skill().isBlank()) {
                continue;
            }
            List<GitHubProfile.GitHubRepository> matched =
                    profile.repositories().stream()
                            .filter(repository -> !repository.fork())
                            .filter(repository -> matches(repository, requirement.skill()))
                            .toList();
            if (matched.isEmpty()) {
                continue;
            }
            ExternalSkillEvidence item = evidence(requirement, matched, now);
            if (item != null && item.q() > 0) {
                evidence.add(item);
            }
        }
        return evidence;
    }

    private ExternalSkillEvidence evidence(
            Requirement requirement,
            List<GitHubProfile.GitHubRepository> repositories,
            Instant now) {
        long bytes = 0;
        double culture = 0;
        Instant earliest = null;
        Instant latest = null;
        boolean bulk = false;

        for (GitHubProfile.GitHubRepository repository : repositories) {
            long languageBytes = languageBytes(repository, requirement.skill());
            bytes +=
                    languageBytes > 0 ? languageBytes : Math.max(0, repository.sizeKb() * 1024 / 2);
            double value = 0;
            if (repository.hasTests()) {
                value += 0.5;
            }
            if (repository.hasCi()) {
                value += 0.3;
            }
            if (repository.hasReadme()) {
                value += 0.2;
            }
            culture += value;
            if (repository.createdAt() != null
                    && (earliest == null || repository.createdAt().isBefore(earliest))) {
                earliest = repository.createdAt();
            }
            if (repository.pushedAt() != null
                    && (latest == null || repository.pushedAt().isAfter(latest))) {
                latest = repository.pushedAt();
            }
            if (repository.createdAt() != null
                    && repository.pushedAt() != null
                    && repository.sizeKb() > 5000
                    && Duration.between(repository.createdAt(), repository.pushedAt()).toDays()
                            < 7) {
                bulk = true;
            }
        }

        double lines = bytes / BYTES_PER_LINE;
        double volume = Math.min(1, Math.log1p(lines) / Math.log1p(L_REF));
        double activeMonths = activeMonths(earliest, latest, now);
        double stability = Math.min(1, activeMonths / 12);
        double cultureScore = culture / repositories.size();
        double rho =
                rho(
                        latest == null ? null : latest.atZone(java.time.ZoneOffset.UTC).getYear(),
                        now.atZone(java.time.ZoneOffset.UTC).getYear(),
                        requirement.halfLifeYears());
        double q = (0.5 * volume + 0.3 * stability + 0.2 * cultureScore) * rho;
        if (bulk) {
            q *= 0.5;
        }
        q = Math.max(0, Math.min(1, q));

        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("repositories", String.valueOf(repositories.size()));
        facts.put("codeLines", String.valueOf(Math.round(lines)));
        facts.put("activeMonths", String.format(Locale.ROOT, "%.1f", activeMonths));
        facts.put(
                "tests",
                String.valueOf(
                        repositories.stream().anyMatch(GitHubProfile.GitHubRepository::hasTests)));
        facts.put(
                "ci",
                String.valueOf(
                        repositories.stream().anyMatch(GitHubProfile.GitHubRepository::hasCi)));
        facts.put(
                "readme",
                String.valueOf(
                        repositories.stream().anyMatch(GitHubProfile.GitHubRepository::hasReadme)));
        facts.put("lastPush", latest == null ? "" : latest.toString());
        Set<String> flags = new LinkedHashSet<>();
        if (bulk) {
            flags.add("RECENT_BULK_UPLOAD");
        }
        return new ExternalSkillEvidence(
                requirement.skill().toLowerCase(Locale.ROOT).strip(), q, facts, List.copyOf(flags));
    }

    private long languageBytes(GitHubProfile.GitHubRepository repository, String skill) {
        for (Map.Entry<String, Long> entry : repository.languages().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(skill)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private boolean matches(GitHubProfile.GitHubRepository repository, String skill) {
        if (skill.equalsIgnoreCase(repository.primaryLanguage())) {
            return true;
        }
        if (repository.languages().keySet().stream().anyMatch(skill::equalsIgnoreCase)) {
            return true;
        }
        String haystack =
                ((repository.description() == null ? "" : repository.description())
                                + " "
                                + String.join(" ", repository.topics())
                                + " "
                                + (repository.readmeText() == null ? "" : repository.readmeText()))
                        .toLowerCase(Locale.ROOT);
        return containsWord(haystack, skill.toLowerCase(Locale.ROOT));
    }

    private boolean containsWord(String haystack, String needle) {
        if (needle.isBlank()) {
            return false;
        }
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}])")
                .matcher(haystack)
                .find();
    }

    /** Свежесть навыка: вклад GitHub затухает с тем же периодом полураспада, что и в модели. */
    private double rho(Integer lastUsedYear, int currentYear, double halfLifeYears) {
        if (lastUsedYear == null || halfLifeYears <= 0) {
            return 1;
        }
        double years = Math.max(0, currentYear - lastUsedYear);
        return Math.pow(0.5, years / halfLifeYears);
    }

    private double activeMonths(Instant earliest, Instant latest, Instant now) {
        if (latest == null) {
            return 0;
        }
        Instant windowStart = now.minus(Duration.ofDays(365));
        Instant start = earliest == null || earliest.isBefore(windowStart) ? windowStart : earliest;
        Instant end = latest.isAfter(now) ? now : latest;
        if (end.isBefore(start)) {
            return 0;
        }
        return Duration.between(start, end).toDays() / 30.44;
    }
}
