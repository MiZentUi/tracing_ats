package ats.resume.infrastructure.report;

import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ExternalEvidenceEntity;
import ats.resume.infrastructure.persistence.entity.RequirementResultEntity;
import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ExternalEvidenceRepository;
import ats.resume.infrastructure.persistence.repository.RequirementResultRepository;
import ats.resume.infrastructure.persistence.repository.ScoreRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Отчёт «требование → доказательство → пробел». PII сюда не попадает. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ApplicationRepository applicationRepository;
    private final ScoreRunRepository scoreRunRepository;
    private final RequirementResultRepository requirementResultRepository;
    private final ExternalEvidenceRepository externalEvidenceRepository;
    private final JsonCodec jsonCodec;

    public Optional<ReportView> report(UUID applicationId, String phase) {
        Optional<ApplicationEntity> application = applicationRepository.findById(applicationId);
        if (application.isEmpty()) {
            return Optional.empty();
        }
        String normalizedPhase = normalizePhase(phase);
        Optional<ScoreRunEntity> run =
                scoreRunRepository.findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(
                        applicationId, normalizedPhase);
        if (run.isEmpty()) {
            return Optional.empty();
        }
        List<RequirementReportItem> requirements =
                requirementResultRepository
                        .findByScoreRunIdOrderByRequirementId(run.get().getId())
                        .stream()
                        .map(this::toRequirement)
                        .toList();
        List<ExternalEvidenceItem> external =
                externalEvidenceRepository
                        .findByScoreRunIdOrderByCreatedAtAsc(run.get().getId())
                        .stream()
                        .map(this::toExternal)
                        .toList();
        return Optional.of(
                new ReportView(
                        applicationId,
                        normalizedPhase,
                        run.get().getScore().doubleValue(),
                        run.get().getScoreLow().doubleValue(),
                        run.get().getScoreHigh().doubleValue(),
                        run.get().isCapApplied(),
                        application.get().getScreeningDecision() == null
                                ? "NOT_SHORTLISTED"
                                : application.get().getScreeningDecision(),
                        requirements,
                        external,
                        flags(run.get()),
                        true));
    }

    private RequirementReportItem toRequirement(RequirementResultEntity entity) {
        return new RequirementReportItem(
                entity.getRequirementId(),
                entity.getRequirementText(),
                entity.getKind(),
                entity.isCritical(),
                entity.getCR().doubleValue(),
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
            String phase,
            double score,
            double scoreLow,
            double scoreHigh,
            boolean capApplied,
            String decision,
            List<RequirementReportItem> requirements,
            List<ExternalEvidenceItem> externalEvidence,
            List<String> flags,
            boolean blind) {}

    public record RequirementReportItem(
            String requirementId,
            String text,
            String kind,
            boolean critical,
            double cR,
            String status,
            String evidenceSource,
            String evidenceQuote,
            String gap) {}

    public record ExternalEvidenceItem(
            String source, String skill, Map<String, String> facts, List<String> flags) {}
}
