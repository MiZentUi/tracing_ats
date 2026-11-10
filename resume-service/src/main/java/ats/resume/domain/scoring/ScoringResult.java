package ats.resume.domain.scoring;

import ats.resume.domain.vacancy.RequirementKind;
import java.util.List;

/** Результат скоринга. Score_pre и Score_final — разные запуски, а не разные поля одного. */
public record ScoringResult(
        double score,
        double scoreLow,
        double scoreHigh,
        boolean capApplied,
        List<String> flags,
        List<RequirementResult> requirements) {

    public ScoringResult {
        flags = List.copyOf(flags);
        requirements = List.copyOf(requirements);
    }

    public record RequirementResult(
            String requirementId,
            String text,
            RequirementKind kind,
            int weight,
            boolean critical,
            double m,
            double t,
            double rho,
            double cR,
            Status status,
            EvidenceSource evidenceSource,
            String evidenceQuote,
            String gap,
            boolean external) {

        public enum Status {
            COVERED,
            PARTIAL,
            GAP
        }

        public enum EvidenceSource {
            RESUME,
            GITHUB
        }
    }
}
