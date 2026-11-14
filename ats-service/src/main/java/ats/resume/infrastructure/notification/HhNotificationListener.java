package ats.resume.infrastructure.notification;

import ats.resume.domain.ingestion.HhApplicationScoredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Ставит алерт только после коммита скоринга. */
@Component
@RequiredArgsConstructor
public class HhNotificationListener {

    private final NotificationOutboxService outboxService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScored(HhApplicationScoredEvent event) {
        outboxService.enqueueHhScored(event);
    }
}
