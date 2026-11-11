package ats.resume.infrastructure.scoring;

import ats.resume.domain.ingestion.ResumeMaskedEvent;
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
        try {
            executor.execute(() -> preliminaryScoringService.scoreOnUpload(event.applicationId()));
        } catch (RuntimeException e) {
            // Отклик не должен остаться в MASKED: иначе скрининг молча его пропустит.
            preliminaryScoringService.markFailed(event.applicationId());
            log.warn(
                    "Preliminary scoring task rejected: applicationId={}, error={}",
                    event.applicationId(),
                    e.getClass().getSimpleName());
        }
    }
}
