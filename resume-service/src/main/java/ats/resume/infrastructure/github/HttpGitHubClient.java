package ats.resume.infrastructure.github;

import ats.resume.config.GitHubProperties;
import ats.resume.domain.enrichment.GitHubClient;
import ats.resume.domain.enrichment.GitHubProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * GitHub REST API без общего «балла GitHub»: забираем только факты по репозиториям, а q_gh считает
 * отдельный слой по конкретному навыку. Логин кандидата не логируется.
 */
@Slf4j
@Component
public class HttpGitHubClient implements GitHubClient {

    private static final int README_LIMIT = 20_000;
    private static final int MAX_REPOS_PER_PAGE = 100;

    private final GitHubProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public HttpGitHubClient(GitHubProperties properties) {
        this.properties = properties;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(properties.getTimeout())
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @Override
    public GitHubProfile fetch(String username) {
        if (!properties.isEnabled()) {
            return new GitHubProfile(username, List.of(), List.of());
        }
        JsonNode repos =
                getJson(
                        "/users/"
                                + encode(username)
                                + "/repos?per_page="
                                + MAX_REPOS_PER_PAGE
                                + "&sort=pushed&direction=desc");
        if (repos == null || !repos.isArray()) {
            return new GitHubProfile(username, List.of(), List.of("GITHUB_PROFILE_NOT_FOUND"));
        }

        List<GitHubProfile.GitHubRepository> result = new ArrayList<>();
        List<JsonNode> selected = new ArrayList<>();
        for (JsonNode repo : repos) {
            if (selected.size() >= properties.getMaxRepos()) {
                break;
            }
            if (repo.path("disabled").asBoolean(false)) {
                continue;
            }
            selected.add(repo);
        }

        boolean hasOwn = selected.stream().anyMatch(repo -> !repo.path("fork").asBoolean(false));
        List<String> flags = new ArrayList<>();
        if (!selected.isEmpty() && !hasOwn) {
            flags.add("FORK_ONLY");
        }

        for (JsonNode repo : selected) {
            result.add(toRepository(username, repo));
        }
        return new GitHubProfile(username, result, flags);
    }

    private GitHubProfile.GitHubRepository toRepository(String owner, JsonNode repo) {
        String name = repo.path("name").asText("");
        boolean fork = repo.path("fork").asBoolean(false);
        String language =
                repo.path("language").isNull() ? null : repo.path("language").asText(null);
        long sizeKb = repo.path("size").asLong(0);
        Instant createdAt = instant(repo.path("created_at").asText(null));
        Instant pushedAt = instant(repo.path("pushed_at").asText(null));
        String description =
                repo.path("description").isNull() ? null : repo.path("description").asText(null);
        List<String> topics = new ArrayList<>();
        repo.path("topics").forEach(topic -> topics.add(topic.asText()));

        Map<String, Long> languages = languages(owner, name);
        TreeFacts tree = treeFacts(owner, name);
        String readme = readme(owner, name);

        return new GitHubProfile.GitHubRepository(
                name,
                fork,
                language,
                languages,
                sizeKb,
                createdAt,
                pushedAt,
                tree.hasTests(),
                tree.hasCi(),
                readme != null,
                description,
                topics,
                readme);
    }

    private Map<String, Long> languages(String owner, String repo) {
        JsonNode node = getJson("/repos/" + encode(owner) + "/" + encode(repo) + "/languages");
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        node.fields()
                .forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asLong(0)));
        return result;
    }

    private TreeFacts treeFacts(String owner, String repo) {
        JsonNode node =
                getJson(
                        "/repos/"
                                + encode(owner)
                                + "/"
                                + encode(repo)
                                + "/git/trees/HEAD?recursive=1");
        if (node == null || !node.path("tree").isArray()) {
            return new TreeFacts(false, false);
        }
        boolean tests = false;
        boolean ci = false;
        for (JsonNode entry : node.path("tree")) {
            String path = entry.path("path").asText("").toLowerCase(java.util.Locale.ROOT);
            if (path.contains("test/")
                    || path.contains("tests/")
                    || path.contains("__tests__/")
                    || path.endsWith(".test.js")
                    || path.endsWith(".spec.ts")
                    || path.endsWith("_test.go")) {
                tests = true;
            }
            if (path.startsWith(".github/workflows/") || path.endsWith(".gitlab-ci.yml")) {
                ci = true;
            }
        }
        return new TreeFacts(tests, ci);
    }

    private String readme(String owner, String repo) {
        JsonNode node = getJson("/repos/" + encode(owner) + "/" + encode(repo) + "/readme");
        if (node == null) {
            return null;
        }
        String content = node.path("content").asText("");
        if (content.isBlank()) {
            return null;
        }
        try {
            String decoded =
                    new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8);
            return decoded.length() > README_LIMIT ? decoded.substring(0, README_LIMIT) : decoded;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private JsonNode getJson(String path) {
        try {
            HttpRequest.Builder request =
                    HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + path))
                            .timeout(properties.getTimeout())
                            .header("Accept", "application/vnd.github+json")
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .header("User-Agent", "tracing-ats");
            if (properties.getToken() != null && !properties.getToken().isBlank()) {
                request.header("Authorization", "Bearer " + properties.getToken());
            }
            HttpResponse<String> response =
                    httpClient.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                throw new GitHubClientException("GitHub API rate limit or access denied");
            }
            if (response.statusCode() >= 400) {
                throw new GitHubClientException(
                        "GitHub API returned status " + response.statusCode());
            }
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new GitHubClientException("GitHub API request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubClientException("GitHub API request interrupted", e);
        }
    }

    private Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record TreeFacts(boolean hasTests, boolean hasCi) {}
}
