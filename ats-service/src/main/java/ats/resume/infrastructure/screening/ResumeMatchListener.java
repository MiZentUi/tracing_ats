package ats.resume.infrastructure.screening;

import ats.resume.domain.ingestion.ResumeMatchRequestedEvent;
import ats.support.logging.RequestIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Запускает сравнение резюме со всеми вакансиями только после коммита загрузки. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeMatchListener {

    private final ResumeMatchService resumeMatchService;

    @Qualifier("screeningExecutor")
    private final TaskExecutor executor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(ResumeMatchRequestedEvent event) {
        String requestId = RequestIds.current();
        try {
            executor.execute(
                    () -> {
                        RequestIds.put(requestId == null ? RequestIds.generate() : requestId);
                        try {
                            resumeMatchService.run(event.batchId(), event.resumeId());
                        } finally {
                            RequestIds.clear();
                        }
                    });
        } catch (RuntimeException e) {
            resumeMatchService.markFailed(event.batchId(), event.resumeId());
            log.warn(
                    "resume_match_rejected batch_id={} resume_id={} error={}",
                    event.batchId(),
                    event.resumeId(),
                    e.getClass().getSimpleName());
        }
    }
}
