package ats.resume.domain.scoring;

import ats.resume.domain.vacancy.VacancyTemplate;
import java.util.List;

/** Порт скоринга. Реализация обязана быть детерминированной при замоканной LLM. */
public interface ScoringClient {

    /** Разбирает резюме один раз. Следующие расчёты используют готовый `ParsedResume`. */
    ParsedResume extract(String maskedText);

    ScoringResult score(ScoringRequest request);

    /** Пакетная оценка одного резюме по нескольким вакансиям одним gRPC-вызовом. */
    List<ScoringBatchItem> scoreBatch(
            ParsedResume parsed, String maskedText, List<VacancyTemplate> templates);

    /** Версия адаптера для неизменяемого запуска скоринга. */
    default String version() {
        return "unknown";
    }
}
