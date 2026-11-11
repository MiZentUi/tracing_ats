package ats.resume.domain.scoring;

import java.util.List;
import java.util.Map;

/**
 * Нормализованный внешний факт по конкретному навыку. Считается в Java, но в текущем gRPC-контракте
 * scoring-service не передается: хранится в external_evidence и показывается в отчете. Отсутствие
 * факта не уменьшает балл.
 */
public record ExternalSkillEvidence(
        String skill, double q, Map<String, String> facts, List<String> flags) {

    public ExternalSkillEvidence {
        facts = Map.copyOf(facts);
        flags = List.copyOf(flags);
    }
}
