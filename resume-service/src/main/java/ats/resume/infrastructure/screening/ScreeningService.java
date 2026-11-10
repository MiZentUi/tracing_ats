package ats.resume.infrastructure.screening;

import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.ScreeningBatchRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/** Запуск пакета. Политика шорт-листа будет применена один раз на весь пакет в worker-е. */
@Service
@RequiredArgsConstructor
public class ScreeningService {

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
            executor.execute(() -> worker.run(batchId, vacancyId, ids));
        }
        return new StartedBatch(batch.getId(), batch.getStatus());
    }

    /** Статус возвращается сразу: пустая пачка завершается синхронно, а не висит в PENDING. */
    public record StartedBatch(UUID batchId, String status) {}

    private List<ApplicationEntity> selectApplications(UUID vacancyId, List<UUID> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return applicationRepository.findByVacancyIdAndStatus(vacancyId, "MASKED");
        }
        return applicationRepository.findByVacancyIdAndIdIn(vacancyId, applicationIds).stream()
                .filter(application -> "MASKED".equals(application.getStatus()))
                .toList();
    }
}
