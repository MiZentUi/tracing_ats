package ats.resume.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ats.gateway.model.ResumeBatchUploadResponse;
import ats.gateway.model.ResumeList;
import ats.gateway.model.ResumeUploadResult;
import ats.resume.domain.ingestion.UploadOutcome;
import ats.resume.infrastructure.ingestion.ResumeIngestionService;
import ats.resume.infrastructure.ingestion.ResumeQueryService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

class ResumeControllerTest {

    @Test
    void listsRecentResumesWithoutPii() {
        ResumeIngestionService ingestion = mock(ResumeIngestionService.class);
        ResumeQueryService query = mock(ResumeQueryService.class);
        UUID resumeId = UUID.randomUUID();
        when(query.list(10, 0))
                .thenReturn(
                        new ResumeQueryService.ResumePage(
                                List.of(
                                        new ResumeQueryService.ResumeView(
                                                resumeId,
                                                "MATCHED",
                                                "TEXT",
                                                1024,
                                                Instant.parse("2026-11-12T19:11:18Z"),
                                                List.of())),
                                1));

        ResponseEntity<ResumeList> response =
                new ResumeController(ingestion, query).listResumes(10, 0);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotal()).isEqualTo(1);
        assertThat(response.getBody().getItems()).hasSize(1);
        assertThat(response.getBody().getItems().getFirst().getResumeId()).isEqualTo(resumeId);
        assertThat(response.getBody().getItems().getFirst().getStatus()).isEqualTo("MATCHED");
    }

    @Test
    void returnsFailedStatusWhenFileCannotBeRead() {
        ResumeIngestionService ingestion = mock(ResumeIngestionService.class);
        ResumeQueryService query = mock(ResumeQueryService.class);
        UUID vacancyId = UUID.randomUUID();
        MockMultipartFile file =
                new MockMultipartFile(
                        "files",
                        "resume.txt",
                        "text/plain",
                        "text".getBytes(StandardCharsets.UTF_8));
        when(ingestion.ingest(any(), anyBoolean(), any()))
                .thenReturn(
                        new UploadOutcome(
                                null, UploadOutcome.Status.FAILED, List.of("FILE_READ_FAILED")));

        ResponseEntity<ResumeBatchUploadResponse> response =
                new ResumeController(ingestion, query)
                        .uploadResumes(List.of(file), vacancyId, false);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResumes()).hasSize(1);
        ResumeUploadResult result = response.getBody().getResumes().getFirst();
        assertThat(result.getStatus()).isEqualTo(ResumeUploadResult.StatusEnum.FAILED);
        assertThat(result.getResumeId()).isNull();
        assertThat(result.getWarnings()).containsExactly("FILE_READ_FAILED");
    }
}
