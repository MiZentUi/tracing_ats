package ats.resume.domain.scoring;

/** Порт скоринга. Реализация обязана быть детерминированной при замоканном LLM. */
public interface ScoringClient {

    ScoringResult score(ScoringRequest request);

    /** Версия адаптера для неизменяемого запуска скоринга. */
    default String version() {
        return "unknown";
    }
}
