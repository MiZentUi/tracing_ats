package ats.resume.infrastructure.github;

/** Ошибка GitHub API. Сообщение не содержит логина кандидата. */
public class GitHubClientException extends RuntimeException {

    public GitHubClientException(String message) {
        super(message);
    }

    public GitHubClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
