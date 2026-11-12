package ats.resume.api;

import ats.api.ScreeningApi;
import ats.gateway.model.ScreeningBatchAccepted;
import ats.gateway.model.ScreeningBatchStatus;
import ats.gateway.model.ScreeningRequest;
import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.screening.ScreeningQueryService;
import ats.resume.infrastructure.screening.ScreeningService;
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
public class ScreeningController implements ScreeningApi {

    private final ScreeningService screeningService;
    private final ScreeningQueryService queryService;

    @Override
    public ResponseEntity<ScreeningBatchAccepted> startScreening(
            UUID vacancyId, ScreeningRequest screeningRequest) {
        List<UUID> applicationIds =
                screeningRequest == null ? List.of() : screeningRequest.getApplicationIds();
        ScreeningService.StartedBatch started = screeningService.start(vacancyId, applicationIds);
        return ResponseEntity.accepted()
                .body(
                        new ScreeningBatchAccepted()
                                .batchId(started.batchId())
                                .status(started.status()));
    }

    @Override
    public ResponseEntity<ScreeningBatchStatus> getScreeningBatch(UUID batchId) {
        return queryService
                .find(batchId)
                .map(
                        batch ->
                                ResponseEntity.ok(
                                        new ScreeningBatchStatus()
                                                .batchId(batch.getId())
                                                .status(
                                                        ScreeningBatchStatus.StatusEnum.valueOf(
                                                                batch.getStatus()))
                                                .total(batch.getTotal())
                                                .processed(batch.getProcessed())
                                                .failed(batch.getFailed())
                                                .createdAt(offset(batch))
                                                .finishedAt(
                                                        batch.getFinishedAt() == null
                                                                ? null
                                                                : batch.getFinishedAt()
                                                                        .atOffset(ZoneOffset.UTC))
                                                .errorMessage(batch.getErrorMessage())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private java.time.OffsetDateTime offset(ScreeningBatchEntity batch) {
        return batch.getCreatedAt() == null ? null : batch.getCreatedAt().atOffset(ZoneOffset.UTC);
    }
}
