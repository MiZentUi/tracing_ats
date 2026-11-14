package ats.resume.api;

import ats.api.ResumesApi;
import ats.gateway.model.ResumeBatchUploadResponse;
import ats.gateway.model.ResumeExtractedEducation;
import ats.gateway.model.ResumeExtractedSkill;
import ats.gateway.model.ResumeExtraction;
import ats.gateway.model.ResumeFullText;
import ats.gateway.model.ResumeList;
import ats.gateway.model.ResumeSummary;
import ats.gateway.model.ResumeText;
import ats.gateway.model.ResumeUploadResult;
import ats.gateway.model.RevealTextRequest;
import ats.gateway.model.UpdateResumeRequest;
import ats.resume.domain.ingestion.UploadOutcome;
import ats.resume.infrastructure.edit.ResumeDeleteService;
import ats.resume.infrastructure.edit.ResumeEditService;
import ats.resume.infrastructure.ingestion.ResumeIngestionService;
import ats.resume.infrastructure.ingestion.ResumeQueryService;
import ats.resume.infrastructure.report.RevealService;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResumeController implements ResumesApi {

    private final ResumeIngestionService ingestionService;
    private final ResumeQueryService queryService;
    private final ResumeEditService editService;
    private final ResumeDeleteService deleteService;
    private final RevealService revealService;

    @Override
    public ResponseEntity<ResumeBatchUploadResponse> uploadResumes(
            List<MultipartFile> files, UUID vacancyId, Boolean consentGithub) {
        List<ResumeUploadResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            UploadOutcome outcome =
                    ingestionService.ingest(vacancyId, Boolean.TRUE.equals(consentGithub), file);
            ResumeUploadResult result =
                    new ResumeUploadResult()
                            .status(ResumeUploadResult.StatusEnum.valueOf(outcome.status().name()))
                            .warnings(outcome.warnings());
            if (outcome.resumeId() != null) {
                result.resumeId(outcome.resumeId());
            }
            if (outcome.batchId() != null) {
                result.batchId(outcome.batchId());
            }
            results.add(result);
        }
        ResumeBatchUploadResponse response =
                new ResumeBatchUploadResponse().vacancyId(vacancyId).resumes(results);
        return ResponseEntity.accepted().body(response);
    }

    @Override
    public ResponseEntity<ResumeList> listResumes(Integer limit, Integer offset) {
        ResumeQueryService.ResumePage page = queryService.list(limit, offset);
        ResumeList response =
                new ResumeList()
                        .total(page.total())
                        .items(page.items().stream().map(this::summary).toList());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ResumeSummary> getResume(UUID resumeId) {
        return queryService
                .find(resumeId)
                .map(view -> ResponseEntity.ok(summary(view)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<ResumeSummary> updateResume(
            UUID resumeId, UpdateResumeRequest updateResumeRequest) {
        return ResponseEntity.ok(summary(editService.update(resumeId, updateResumeRequest)));
    }

    @Override
    public ResponseEntity<Void> deleteResume(UUID resumeId) {
        deleteService.delete(resumeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ResumeText> getResumeText(UUID resumeId) {
        return queryService
                .text(resumeId)
                .map(
                        view ->
                                ResponseEntity.ok(
                                        new ResumeText()
                                                .resumeId(view.resumeId())
                                                .maskedText(view.maskedText())
                                                .warnings(view.warnings())
                                                .blind(true)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<ResumeExtraction> getResumeExtraction(UUID resumeId) {
        return queryService
                .extraction(resumeId)
                .map(
                        view ->
                                ResponseEntity.ok(
                                        new ResumeExtraction()
                                                .resumeId(view.resumeId())
                                                .skills(
                                                        view.skills().stream()
                                                                .map(
                                                                        skill ->
                                                                                new ResumeExtractedSkill()
                                                                                        .name(
                                                                                                skill
                                                                                                        .name())
                                                                                        .normalizedName(
                                                                                                skill
                                                                                                        .normalizedName())
                                                                                        .tenure(
                                                                                                skill
                                                                                                        .tenure())
                                                                                        .recency(
                                                                                                skill
                                                                                                        .recency())
                                                                                        .evidenceQuote(
                                                                                                skill
                                                                                                        .evidenceQuote()))
                                                                .toList())
                                                .education(
                                                        view.education().stream()
                                                                .map(
                                                                        education ->
                                                                                new ResumeExtractedEducation()
                                                                                        .institution(
                                                                                                education
                                                                                                        .institution())
                                                                                        .specialty(
                                                                                                education
                                                                                                        .specialty())
                                                                                        .level(
                                                                                                education
                                                                                                        .level())
                                                                                        .graduationYear(
                                                                                                education
                                                                                                        .graduationYear())
                                                                                        .evidenceQuote(
                                                                                                education
                                                                                                        .evidenceQuote()))
                                                                .toList())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<ResumeFullText> revealResumeText(
            UUID resumeId, RevealTextRequest request) {
        return revealService
                .revealText(resumeId, request.getApplicationId(), request.getReason())
                .map(
                        text ->
                                ResponseEntity.ok(
                                        new ResumeFullText()
                                                .resumeId(resumeId)
                                                .text(text)
                                                .blind(false)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResumeSummary summary(ResumeQueryService.ResumeView view) {
        return new ResumeSummary()
                .resumeId(view.resumeId())
                .title(view.title())
                .status(view.status())
                .fileType(view.fileType())
                .sizeBytes(view.sizeBytes())
                .createdAt(view.createdAt().atOffset(ZoneOffset.UTC))
                .warnings(view.warnings());
    }
}
