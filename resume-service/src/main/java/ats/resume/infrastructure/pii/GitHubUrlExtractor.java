package ats.resume.infrastructure.pii;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Достаёт ссылку на GitHub до маскировки: она нужна для обогащения и хранится в vault. */
@Component
public class GitHubUrlExtractor {

    private static final Pattern GITHUB_URL =
            Pattern.compile(
                    "\\b(?:https?://)?(?:www\\.)?github\\.com/[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})",
                    Pattern.CASE_INSENSITIVE);

    private static final Set<String> RESERVED =
            Set.of(
                    "features",
                    "topics",
                    "about",
                    "pricing",
                    "login",
                    "join",
                    "marketplace",
                    "explore",
                    "settings",
                    "notifications",
                    "orgs",
                    "users",
                    "search",
                    "collections",
                    "trending",
                    "sponsors",
                    "readme");

    public String find(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = GITHUB_URL.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            String username = username(value);
            if (username != null && !RESERVED.contains(username.toLowerCase(Locale.ROOT))) {
                return "https://github.com/" + username;
            }
        }
        return null;
    }

    public String username(String githubUrl) {
        if (githubUrl == null) {
            return null;
        }
        String value = githubUrl.trim();
        int index = value.toLowerCase(Locale.ROOT).indexOf("github.com/");
        if (index < 0) {
            return null;
        }
        String tail = value.substring(index + "github.com/".length());
        String username = tail.split("[/?#]", 2)[0];
        if (username.isBlank() || RESERVED.contains(username.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return username;
    }
}
