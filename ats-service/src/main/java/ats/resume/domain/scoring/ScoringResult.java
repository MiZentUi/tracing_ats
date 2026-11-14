package ats.resume.domain.scoring;

import ats.resume.domain.vacancy.RequirementKind;
import java.util.List;

/** Результат скоринга. Score_pre и Score_final — разные запуски, а не разные поля одного. */
public record ScoringResult(
        double relevance,
        double score,
        double scoreLow,
        double scoreHigh,
        Integer tenure,
        List<String> flags,
        List<RequirementResult> requirements,
        List<Gap> gaps,
        List<String> strengths,
        List<String> risks,
        List<String> questions,
        String recruiterNote) {

    public ScoringResult {
        flags = List.copyOf(flags);
        requirements = List.copyOf(requirements);
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        risks = risks == null ? List.of() : List.copyOf(risks);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }

    public ScoringResult(
            double relevance,
            double score,
            double scoreLow,
            double scoreHigh,
            Integer tenure,
            List<String> flags,
            List<RequirementResult> requirements) {
        this(
                relevance,
                score,
                scoreLow,
                scoreHigh,
                tenure,
                flags,
                requirements,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    public record Gap(String requirementId, String reason) {}

    public record RequirementResult(
            String requirementId,
            String text,
            RequirementKind kind,
            int weight,
            boolean critical,
            double relevance,
            double coverage,
            Integer tenure,
            Status status,
            EvidenceSource evidenceSource,
            String evidenceQuote,
            String gap,
            boolean external) {

        /** Статусы ТЗ: полное, частичное, отсутствие и «не указано». */
        public enum Status {
            COVERED,
            PARTIAL,
            NOT_STATED,
            ABSENT,
            GAP
        }

        public enum EvidenceSource {
            RESUME,
            GITHUB
        }
    }
}
