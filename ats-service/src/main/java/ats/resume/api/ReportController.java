package ats.resume.api;

import ats.api.ReportsApi;
import ats.gateway.model.ApplicationReport;
import ats.gateway.model.CandidateMessage;
import ats.gateway.model.CandidateMessageKind;
import ats.gateway.model.CandidateMessageRequest;
import ats.gateway.model.ExternalEvidenceItem;
import ats.gateway.model.ReportDecision;
import ats.gateway.model.ReportDecisionRequest;
import ats.gateway.model.ReportGap;
import ats.gateway.model.RequirementReportItem;
import ats.gateway.model.RevealRequest;
import ats.gateway.model.RevealedCandidate;
import ats.resume.infrastructure.report.CandidateMessageService;
import ats.resume.infrastructure.report.ReportDecisionService;
import ats.resume.infrastructure.report.ReportService;
import ats.resume.infrastructure.report.RevealService;
import ats.vacancy.application.ApiException;
import java.time.ZoneOffset;
import java.util.List;
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
    private final ReportDecisionService decisionService;
    private final RevealService revealService;
    private final CandidateMessageService candidateMessageService;

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
    public ResponseEntity<ReportDecision> saveReportDecision(
            UUID resumeId, UUID vacancyId, ReportDecisionRequest reportDecisionRequest) {
        ReportDecisionService.DecisionView view =
                decisionService.save(resumeId, vacancyId, reportDecisionRequest);
        return ResponseEntity.ok(
                new ReportDecision()
                        .resumeId(view.resumeId())
                        .vacancyId(view.vacancyId())
                        .decision(ReportDecision.DecisionEnum.valueOf(view.decision()))
                        .comment(view.comment())
                        .updatedAt(view.updatedAt().atOffset(ZoneOffset.UTC)));
    }

    @Override
    public ResponseEntity<List<CandidateMessage>> getCandidateMessages(
            UUID applicationId, String phase) {
        return ResponseEntity.ok(
                candidateMessageService.latest(applicationId, phase).stream()
                        .map(this::candidateMessage)
                        .toList());
    }

    @Override
    public ResponseEntity<CandidateMessage> generateCandidateMessage(
            UUID applicationId, CandidateMessageRequest candidateMessageRequest, String phase) {
        CandidateMessageKind kind = candidateMessageRequest.getKind();
        if (kind == null) {
            throw ApiException.unprocessable("KIND_REQUIRED", "Candidate message kind is required");
        }
        return ResponseEntity.ok(
                candidateMessage(candidateMessageService.generate(applicationId, phase, kind)));
    }

    @Override
    public ResponseEntity<List<CandidateMessage>> getResumeVacancyCandidateMessages(
            UUID resumeId, UUID vacancyId) {
        return ResponseEntity.ok(
                candidateMessageService.latestForMatch(resumeId, vacancyId).stream()
                        .map(this::candidateMessage)
                        .toList());
    }

    @Override
    public ResponseEntity<CandidateMessage> generateResumeVacancyCandidateMessage(
            UUID resumeId, UUID vacancyId, CandidateMessageRequest candidateMessageRequest) {
        CandidateMessageKind kind = candidateMessageRequest.getKind();
        if (kind == null) {
            throw ApiException.unprocessable("KIND_REQUIRED", "Candidate message kind is required");
        }
        return ResponseEntity.ok(
                candidateMessage(
                        candidateMessageService.generateForMatch(resumeId, vacancyId, kind)));
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
                .hrDecision(
                        report.hrDecision() == null
                                ? null
                                : ApplicationReport.HrDecisionEnum.valueOf(report.hrDecision()))
                .hrComment(report.hrComment())
                .requirements(report.requirements().stream().map(this::requirement).toList())
                .externalEvidence(report.externalEvidence().stream().map(this::external).toList())
                .flags(report.flags())
                .blind(report.blind())
                .strengths(report.strengths())
                .risks(report.risks())
                .questions(report.questions())
                .recruiterNote(report.recruiterNote())
                .gaps(report.gaps().stream().map(this::gap).toList());
    }

    private CandidateMessage candidateMessage(CandidateMessageService.View view) {
        return new CandidateMessage()
                .id(view.id())
                .applicationId(view.applicationId())
                .resumeId(view.resumeId())
                .vacancyId(view.vacancyId())
                .phase(CandidateMessage.PhaseEnum.valueOf(view.phase()))
                .kind(CandidateMessageKind.valueOf(view.kind()))
                .subject(view.subject())
                .body(view.body())
                .promptVersion(view.promptVersion())
                .model(view.model())
                .createdAt(view.createdAt().atOffset(ZoneOffset.UTC));
    }

    private ReportGap gap(ReportService.ReportGap item) {
        return new ReportGap()
                .requirementId(item.requirementId())
                .text(item.text())
                .reason(item.reason());
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
