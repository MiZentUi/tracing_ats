package ats.resume.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ats.gateway.model.ResumeBatchUploadResponse;
import ats.gateway.model.ResumeUploadResult;
import ats.resume.domain.ingestion.UploadOutcome;
import ats.resume.infrastructure.ingestion.ResumeIngestionService;
import ats.resume.infrastructure.ingestion.ResumeQueryService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

class ResumeControllerTest {

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
                        .uploadResumes(vacancyId, List.of(file), false);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResumes()).hasSize(1);
        ResumeUploadResult result = response.getBody().getResumes().getFirst();
        assertThat(result.getStatus()).isEqualTo(ResumeUploadResult.StatusEnum.FAILED);
        assertThat(result.getResumeId()).isNull();
        assertThat(result.getWarnings()).containsExactly("FILE_READ_FAILED");
    }
}
