package ats.resume.infrastructure.scoring;

import ats.resume.domain.ingestion.ResumeMaskedEvent;
import ats.support.logging.RequestIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Запускает предварительный скоринг только после коммита загрузки: иначе worker может не увидеть
 * application и parsed_resume. Сам вызов уходит в отдельный пул.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreliminaryScoringListener {

    private final PreliminaryScoringService preliminaryScoringService;

    @Qualifier("scoringExecutor")
    private final TaskExecutor executor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResumeMasked(ResumeMaskedEvent event) {
        String requestId = RequestIds.current();
        try {
            executor.execute(
                    () -> {
                        // Фоновая задача продолжает цепочку загрузки: сохраняем её request_id.
                        RequestIds.put(requestId == null ? RequestIds.generate() : requestId);
                        try {
                            preliminaryScoringService.scoreOnUpload(event.applicationId());
                        } finally {
                            RequestIds.clear();
                        }
                    });
        } catch (RuntimeException e) {
            // Отклик не должен остаться в MASKED: иначе скрининг молча его пропустит.
            preliminaryScoringService.markFailed(event.applicationId());
            log.warn(
                    "preliminary_scoring_rejected application_id={} error={}",
                    event.applicationId(),
                    e.getClass().getSimpleName());
        }
    }
}
