package ats.resume.infrastructure.screening;

import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ScreeningBatchRepository;
import ats.support.logging.RequestIds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Запуск пакета. Предварительные баллы уже посчитаны при загрузке; worker только ждёт их,
 * пересчитывает устаревшие и применяет политику шорт-листа один раз на весь пакет.
 */
@Service
@RequiredArgsConstructor
public class ScreeningService {

    private static final List<String> READY_STATUSES =
            List.of("SCORED", "SCORING", "SCORING_FAILED");

    private final ApplicationRepository applicationRepository;
    private final ScreeningBatchRepository batchRepository;
    private final ScreeningWorker worker;

    @Qualifier("screeningExecutor")
    private final TaskExecutor executor;

    public StartedBatch start(UUID vacancyId, List<UUID> applicationIds) {
        List<ApplicationEntity> applications = selectApplications(vacancyId, applicationIds);
        ScreeningBatchEntity batch = new ScreeningBatchEntity();
        batch.setVacancyId(vacancyId);
        batch.setStatus(applications.isEmpty() ? "COMPLETED" : "PENDING");
        batch.setTotal(applications.size());
        batch.setProcessed(0);
        batch.setFailed(0);
        if (applications.isEmpty()) {
            batch.setFinishedAt(Instant.now());
        }
        batch = batchRepository.save(batch);

        if (!applications.isEmpty()) {
            List<UUID> ids = applications.stream().map(ApplicationEntity::getId).toList();
            UUID batchId = batch.getId();
            String requestId = RequestIds.current();
            executor.execute(
                    () -> {
                        RequestIds.put(requestId == null ? RequestIds.generate() : requestId);
                        try {
                            worker.run(batchId, vacancyId, ids);
                        } finally {
                            RequestIds.clear();
                        }
                    });
        }
        return new StartedBatch(batch.getId(), batch.getStatus());
    }

    /** Статус возвращается сразу: пустая пачка завершается синхронно, а не висит в PENDING. */
    public record StartedBatch(UUID batchId, String status) {}

    private List<ApplicationEntity> selectApplications(UUID vacancyId, List<UUID> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return applicationRepository.findByVacancyIdAndStatusIn(vacancyId, READY_STATUSES);
        }
        return applicationRepository.findByVacancyIdAndIdIn(vacancyId, applicationIds).stream()
                .filter(application -> READY_STATUSES.contains(application.getStatus()))
                .toList();
    }
}
