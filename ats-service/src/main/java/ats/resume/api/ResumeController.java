package ats.resume.api;

import ats.api.ResumesApi;
import ats.gateway.model.ResumeBatchUploadResponse;
import ats.gateway.model.ResumeSummary;
import ats.gateway.model.ResumeUploadResult;
import ats.resume.domain.ingestion.UploadOutcome;
import ats.resume.infrastructure.ingestion.ResumeIngestionService;
import ats.resume.infrastructure.ingestion.ResumeQueryService;
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

    @Override
    public ResponseEntity<ResumeBatchUploadResponse> uploadResumes(
            UUID vacancyId, List<MultipartFile> files, Boolean consentGithub) {
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
            results.add(result);
        }
        ResumeBatchUploadResponse response =
                new ResumeBatchUploadResponse().vacancyId(vacancyId).resumes(results);
        return ResponseEntity.accepted().body(response);
    }

    @Override
    public ResponseEntity<ResumeSummary> getResume(UUID resumeId) {
        return queryService
                .find(resumeId)
                .map(
                        view ->
                                ResponseEntity.ok(
                                        new ResumeSummary()
                                                .resumeId(view.resumeId())
                                                .status(view.status())
                                                .fileType(view.fileType())
                                                .sizeBytes(view.sizeBytes())
                                                .createdAt(
                                                        view.createdAt().atOffset(ZoneOffset.UTC))
                                                .warnings(view.warnings())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
