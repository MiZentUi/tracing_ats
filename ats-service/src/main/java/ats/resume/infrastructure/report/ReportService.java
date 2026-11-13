package ats.resume.infrastructure.report;

import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ExternalEvidenceEntity;
import ats.resume.infrastructure.persistence.entity.RequirementResultEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ExternalEvidenceRepository;
import ats.resume.infrastructure.persistence.repository.ReportDecisionRepository;
import ats.resume.infrastructure.persistence.repository.RequirementResultRepository;
import ats.resume.infrastructure.persistence.repository.ScoreRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Отчёт «требование → доказательство → пробел» для обоих направлений. PII сюда не попадает. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ApplicationRepository applicationRepository;
    private final ScoreRunRepository scoreRunRepository;
    private final RequirementResultRepository requirementResultRepository;
    private final ExternalEvidenceRepository externalEvidenceRepository;
    private final ReportDecisionRepository decisionRepository;
    private final JsonCodec jsonCodec;

    public Optional<ReportView> report(UUID applicationId, String phase) {
        Optional<ApplicationEntity> application = applicationRepository.findById(applicationId);
        if (application.isEmpty()) {
            return Optional.empty();
        }
        String normalizedPhase = normalizePhase(phase);
        UUID runId =
                "FINAL".equals(normalizedPhase)
                        ? application.get().getFinalScoreRunId()
                        : application.get().getPreliminaryScoreRunId();
        if (runId == null) {
            return Optional.empty();
        }
        return scoreRunRepository
                .findById(runId)
                .map(
                        run ->
                                view(
                                        applicationId,
                                        application.get().getResumeId(),
                                        application.get().getVacancyId(),
                                        normalizedPhase,
                                        run,
                                        application.get().getScreeningDecision()));
    }

    /** Отчёт направления «вакансии под резюме»: отклика нет, есть пара резюме × вакансия. */
    public Optional<ReportView> reportForMatch(UUID resumeId, UUID vacancyId, String phase) {
        String normalizedPhase = normalizePhase(phase);
        return scoreRunRepository
                .findFirstByResumeIdAndVacancyIdAndDirectionAndPhaseOrderByCreatedAtDesc(
                        resumeId, vacancyId, "RESUME_TO_VACANCIES", normalizedPhase)
                .map(run -> view(null, resumeId, vacancyId, normalizedPhase, run, null));
    }

    private ReportView view(
            UUID applicationId,
            UUID resumeId,
            UUID vacancyId,
            String phase,
            ScoreRunEntity run,
            String decision) {
        List<RequirementReportItem> requirements =
                requirementResultRepository
                        .findByScoreRunIdOrderByRequirementId(run.getId())
                        .stream()
                        .map(this::toRequirement)
                        .toList();
        List<ExternalEvidenceItem> external =
                externalEvidenceRepository.findByScoreRunIdOrderByCreatedAtAsc(run.getId()).stream()
                        .map(this::toExternal)
                        .toList();
        var hrDecision =
                decisionRepository.findByResumeIdAndVacancyId(resumeId, vacancyId).orElse(null);
        return new ReportView(
                applicationId,
                resumeId,
                vacancyId,
                phase,
                run.getRelevance() == null ? null : run.getRelevance().doubleValue(),
                run.getScore().doubleValue(),
                run.getScoreLow().doubleValue(),
                run.getScoreHigh().doubleValue(),
                run.isCapApplied(),
                decision,
                requirements,
                external,
                flags(run),
                true,
                hrDecision == null ? null : hrDecision.getDecision(),
                hrDecision == null ? null : hrDecision.getComment());
    }

    private RequirementReportItem toRequirement(RequirementResultEntity entity) {
        return new RequirementReportItem(
                entity.getRequirementId(),
                entity.getRequirementText(),
                entity.getKind(),
                entity.isCritical(),
                entity.getRelevance() == null ? null : entity.getRelevance().doubleValue(),
                entity.getCR().doubleValue(),
                entity.getTenure(),
                entity.getStatus(),
                entity.getEvidenceSource(),
                entity.getEvidenceQuote(),
                entity.getGap());
    }

    private ExternalEvidenceItem toExternal(ExternalEvidenceEntity entity) {
        Map<String, String> facts;
        List<String> flags;
        try {
            facts = jsonCodec.read(entity.getFacts(), new TypeReference<Map<String, String>>() {});
        } catch (RuntimeException e) {
            facts = Map.of();
        }
        try {
            flags = jsonCodec.readList(entity.getFlags(), new TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            flags = List.of();
        }
        return new ExternalEvidenceItem(entity.getSource(), entity.getSkill(), facts, flags);
    }

    private List<String> flags(ScoreRunEntity run) {
        try {
            return jsonCodec.readList(run.getFlags(), new TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private String normalizePhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return "FINAL";
        }
        String value = phase.toUpperCase(java.util.Locale.ROOT);
        if (!value.equals("PRELIMINARY") && !value.equals("FINAL")) {
            throw new IllegalArgumentException("phase must be PRELIMINARY or FINAL");
        }
        return value;
    }

    public record ReportView(
            UUID applicationId,
            UUID resumeId,
            UUID vacancyId,
            String phase,
            Double relevance,
            double score,
            double scoreLow,
            double scoreHigh,
            boolean capApplied,
            String decision,
            List<RequirementReportItem> requirements,
            List<ExternalEvidenceItem> externalEvidence,
            List<String> flags,
            boolean blind,
            String hrDecision,
            String hrComment) {}

    public record RequirementReportItem(
            String requirementId,
            String text,
            String kind,
            boolean critical,
            Double relevance,
            double cR,
            Integer tenure,
            String status,
            String evidenceSource,
            String evidenceQuote,
            String gap) {}

    public record ExternalEvidenceItem(
            String source, String skill, Map<String, String> facts, List<String> flags) {}
}
