package ats.resume.api;

import ats.resume.api.generated.ReportsApi;
import ats.resume.api.model.ApplicationReport;
import ats.resume.api.model.ExternalEvidenceItem;
import ats.resume.api.model.RequirementReportItem;
import ats.resume.api.model.RevealRequest;
import ats.resume.api.model.RevealedCandidate;
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
                .map(
                        report ->
                                ResponseEntity.ok(
                                        new ApplicationReport()
                                                .applicationId(report.applicationId())
                                                .phase(
                                                        ApplicationReport.PhaseEnum.valueOf(
                                                                report.phase()))
                                                .score(report.score())
                                                .scoreLow(report.scoreLow())
                                                .scoreHigh(report.scoreHigh())
                                                .capApplied(report.capApplied())
                                                .decision(
                                                        ApplicationReport.DecisionEnum.valueOf(
                                                                report.decision()))
                                                .requirements(
                                                        report.requirements().stream()
                                                                .map(this::requirement)
                                                                .toList())
                                                .externalEvidence(
                                                        report.externalEvidence().stream()
                                                                .map(this::external)
                                                                .toList())
                                                .flags(report.flags())
                                                .blind(report.blind())))
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

    private RequirementReportItem requirement(ReportService.RequirementReportItem item) {
        return new RequirementReportItem()
                .requirementId(item.requirementId())
                .text(item.text())
                .kind(RequirementReportItem.KindEnum.valueOf(item.kind()))
                .critical(item.critical())
                .cR(item.cR())
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
