package ats.vacancy.template;

import java.util.List;
import java.util.UUID;

/** Снимок шаблона вакансии, который уходит в resume-service по gRPC. */
public record VacancyTemplateData(
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
        List<RequirementData> requirements) {

    public VacancyTemplateData {
        requirements = List.copyOf(requirements);
    }

    public record RequirementData(
            UUID id,
            String text,
            String skill,
            String kind,
            int weight,
            boolean critical,
            double yearsRequired,
            double halfLifeYears) {}
}
