package ats.resume.api;

import ats.api.ReportsApi;
import ats.gateway.model.ApplicationReport;
import ats.gateway.model.ExternalEvidenceItem;
import ats.gateway.model.RequirementReportItem;
import ats.gateway.model.RevealRequest;
import ats.gateway.model.RevealedCandidate;
import ats.resume.infrastructure.report.ReportService;
import ats.resume.infrastructure.report.RevealService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController implements ReportsApi {

    private final ReportService reportService;
    private final RevealService revealService;

    @Override
    public ResponseEntity<ApplicationReport> getApplicationReport(
            UUID applicationId, String phase) {
        return reportService
                .report(applicationId, phase)
                .map(report -> ResponseEntity.ok(response(report)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<ApplicationReport> getResumeVacancyReport(UUID resumeId, UUID vacancyId) {
        return reportService
                .reportForMatch(resumeId, vacancyId, "PRELIMINARY")
                .map(report -> ResponseEntity.ok(response(report)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<RevealedCandidate> revealCandidate(
            UUID applicationId, RevealRequest revealRequest) {
        return revealService
                .reveal(applicationId, revealRequest.getReason())
                .map(
                        candidate ->
                                ResponseEntity.ok(
                                        new RevealedCandidate()
                                                .fullName(candidate.fullName())
                                                .email(candidate.email())
                                                .phone(candidate.phone())
                                                .university(candidate.university())
                                                .graduationYear(candidate.graduationYear())
                                                .githubUrl(candidate.githubUrl())
                                                .originalFilename(candidate.originalFilename())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ApplicationReport response(ReportService.ReportView report) {
        return new ApplicationReport()
                .applicationId(report.applicationId())
                .resumeId(report.resumeId())
                .vacancyId(report.vacancyId())
                .phase(ApplicationReport.PhaseEnum.valueOf(report.phase()))
                .relevance(report.relevance())
                .score(report.score())
                .scoreLow(report.scoreLow())
                .scoreHigh(report.scoreHigh())
                .capApplied(report.capApplied())
                .decision(
                        report.decision() == null
                                ? null
                                : ApplicationReport.DecisionEnum.valueOf(report.decision()))
                .requirements(report.requirements().stream().map(this::requirement).toList())
                .externalEvidence(report.externalEvidence().stream().map(this::external).toList())
                .flags(report.flags())
                .blind(report.blind());
    }

    private RequirementReportItem requirement(ReportService.RequirementReportItem item) {
        return new RequirementReportItem()
                .requirementId(item.requirementId())
                .text(item.text())
                .kind(RequirementReportItem.KindEnum.valueOf(item.kind()))
                .critical(item.critical())
                .relevance(item.relevance())
                .cR(item.cR())
                .tenure(item.tenure())
                .status(RequirementReportItem.StatusEnum.valueOf(item.status()))
                .evidenceSource(
                        item.evidenceSource() == null
                                ? null
                                : RequirementReportItem.EvidenceSourceEnum.valueOf(
                                        item.evidenceSource()))
                .evidenceQuote(item.evidenceQuote())
                .gap(item.gap());
    }

    private ExternalEvidenceItem external(ReportService.ExternalEvidenceItem item) {
        return new ExternalEvidenceItem()
                .source(ExternalEvidenceItem.SourceEnum.valueOf(item.source()))
                .skill(item.skill())
                .facts(item.facts())
                .flags(item.flags());
    }
}
