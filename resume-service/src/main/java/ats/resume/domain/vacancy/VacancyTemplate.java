package ats.resume.domain.vacancy;

import java.util.List;
import java.util.UUID;

/** Снимок шаблона вакансии, полученный из vacancy-service по gRPC. */
public record VacancyTemplate(
        UUID vacancyId,
        int version,
        String title,
        String specializationCode,
        String levelCode,
        double githubWeight,
        double wM,
        double wN,
        double wF,
        String promptVersion,
        String scoringVersion,
        String weightsVersion,
        List<Requirement> requirements) {

    public VacancyTemplate {
        requirements = List.copyOf(requirements);
    }
}
