package ats.resume.domain.enrichment;

/** Порт внешнего источника. Отсутствие GitHub не должно уменьшать балл. */
public interface GitHubClient {

    GitHubProfile fetch(String username);
}
