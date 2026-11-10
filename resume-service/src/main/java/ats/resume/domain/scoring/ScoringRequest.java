package ats.resume.domain.scoring;

import ats.resume.domain.privacy.EducationFacts;
import ats.resume.domain.vacancy.VacancyTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Запрос на скоринг. В реальном контракте это gRPC в scoring-service; пока используется локальный
 * детерминированный адаптер. Текст уже обезличен и очищен от prompt injection. Факты об образовании
 * не содержат вуза и года выпуска и передаются отдельно от текста.
 */
public record ScoringRequest(
        UUID applicationId,
        String maskedText,
        EducationFacts education,
        VacancyTemplate template,
        Map<String, ExternalSkillEvidence> externalEvidence,
        Instant now) {

    public ScoringRequest {
        education = education == null ? EducationFacts.empty() : education;
        externalEvidence = Map.copyOf(externalEvidence);
    }

    public ScoringRequest withExternalEvidence(Map<String, ExternalSkillEvidence> evidence) {
        return new ScoringRequest(applicationId, maskedText, education, template, evidence, now);
    }

    public record ExternalSkillEvidence(
            String skill, double q, Map<String, String> facts, List<String> flags) {

        public ExternalSkillEvidence {
            facts = Map.copyOf(facts);
            flags = List.copyOf(flags);
        }
    }
}
