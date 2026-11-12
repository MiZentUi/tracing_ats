package ats.resume.domain.enrichment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Нормализованный профиль GitHub. Сырые ответы API и PII дальше не уходят. */
public record GitHubProfile(
        String username, List<GitHubRepository> repositories, List<String> flags) {

    public GitHubProfile {
        repositories = List.copyOf(repositories);
        flags = List.copyOf(flags);
    }

    public record GitHubRepository(
            String name,
            boolean fork,
            String primaryLanguage,
            Map<String, Long> languages,
            long sizeKb,
            Instant createdAt,
            Instant pushedAt,
            boolean hasTests,
            boolean hasCi,
            boolean hasReadme,
            String description,
            List<String> topics,
            String readmeText) {

        public GitHubRepository {
            languages = Map.copyOf(languages);
            topics = List.copyOf(topics);
        }
    }
}
