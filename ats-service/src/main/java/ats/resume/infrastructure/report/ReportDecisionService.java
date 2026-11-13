package ats.resume.infrastructure.report;

import ats.gateway.model.ReportDecisionRequest;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.AuditEventEntity;
import ats.resume.infrastructure.persistence.entity.ReportDecisionEntity;
import ats.resume.infrastructure.persistence.repository.AuditEventRepository;
import ats.resume.infrastructure.persistence.repository.ReportDecisionRepository;
import ats.resume.infrastructure.persistence.repository.ResumeRepository;
import ats.vacancy.application.ApiException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Решение HR по отчёту. Сохраняется отдельно от решения модели и не запускает действий. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportDecisionService {

    private final ReportDecisionRepository decisionRepository;
    private final ResumeRepository resumeRepository;
    private final AuditEventRepository auditEventRepository;
    private final JsonCodec jsonCodec;

    @Transactional
    public DecisionView save(UUID resumeId, UUID vacancyId, ReportDecisionRequest request) {
        if (!resumeRepository.existsById(resumeId)) {
            throw ApiException.notFound("Resume not found");
        }
        String comment = request.getComment() == null ? "" : request.getComment().strip();
        if (comment.length() < 3) {
            throw ApiException.unprocessable(
                    "COMMENT_REQUIRED", "Comment is required when saving a decision");
        }
        if (request.getDecision() == null) {
            throw ApiException.unprocessable("DECISION_REQUIRED", "Decision is required");
        }

        ReportDecisionEntity entity =
                decisionRepository
                        .findByResumeIdAndVacancyId(resumeId, vacancyId)
                        .orElseGet(ReportDecisionEntity::new);
        entity.setResumeId(resumeId);
        entity.setVacancyId(vacancyId);
        entity.setDecision(request.getDecision().name());
        entity.setComment(comment);
        entity = decisionRepository.save(entity);

        AuditEventEntity audit = new AuditEventEntity();
        audit.setActor("hr");
        audit.setAction("REPORT_DECISION");
        audit.setReason(comment);
        audit.setMetadata(
                jsonCodec.write(
                        Map.of(
                                "resumeId", resumeId.toString(),
                                "vacancyId", vacancyId.toString(),
                                "decision", entity.getDecision())));
        auditEventRepository.save(audit);

        log.info(
                "report_decision_saved resume_id={} vacancy_id={} decision={}",
                resumeId,
                vacancyId,
                entity.getDecision());
        return new DecisionView(
                resumeId,
                vacancyId,
                entity.getDecision(),
                entity.getComment(),
                entity.getUpdatedAt());
    }

    public record DecisionView(
            UUID resumeId, UUID vacancyId, String decision, String comment, Instant updatedAt) {}
}
