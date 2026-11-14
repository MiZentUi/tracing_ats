package ats.resume.infrastructure.notification;

import ats.resume.domain.ingestion.HhApplicationScoredEvent;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.NotificationOutboxEntity;
import ats.resume.infrastructure.persistence.repository.NotificationOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Кладёт алерт в outbox. Дедупликация по application_id + event_type: повторный скоринг не отправит
 * второе сообщение.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private final NotificationOutboxRepository repository;
    private final JsonCodec jsonCodec;
    private final TelegramPropertiesProvider properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueHhScored(HhApplicationScoredEvent event) {
        if (!properties.enabled()) {
            log.debug("telegram_notification_skipped reason=disabled");
            return;
        }
        String dedupeKey = "HH_SCORED:" + event.applicationId();
        if (repository.findByDedupeKey(dedupeKey).isPresent()) {
            return;
        }
        HhScoreNotificationPayload payload =
                new HhScoreNotificationPayload(
                        event.vacancyId(),
                        event.vacancyTitle(),
                        event.score(),
                        event.scoreLow(),
                        event.scoreHigh());
        NotificationOutboxEntity entity = new NotificationOutboxEntity();
        entity.setDedupeKey(dedupeKey);
        entity.setEventType("HH_SCORED");
        entity.setVacancyId(event.vacancyId());
        entity.setApplicationId(event.applicationId());
        entity.setPayload(jsonCodec.write(payload));
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        entity.setNextAttemptAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        try {
            repository.save(entity);
            log.info(
                    "notification_outbox_enqueued event_type=HH_SCORED application_id={} vacancy_id={}",
                    event.applicationId(),
                    event.vacancyId());
        } catch (DataIntegrityViolationException e) {
            // Параллельный слушатель уже поставил тот же алерт.
            log.debug("notification_outbox_duplicate dedupe_key={}", dedupeKey);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueHhUnmatched(String hhVacancyId) {
        if (!properties.enabled()) {
            return;
        }
        String dedupeKey = "HH_UNMATCHED:" + hhVacancyId;
        if (repository.findByDedupeKey(dedupeKey).isPresent()) {
            return;
        }
        NotificationOutboxEntity entity = new NotificationOutboxEntity();
        entity.setDedupeKey(dedupeKey);
        entity.setEventType("HH_UNMATCHED");
        entity.setPayload(jsonCodec.write(new HhUnmatchedPayload(hhVacancyId)));
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        entity.setNextAttemptAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        try {
            repository.save(entity);
            log.info(
                    "notification_outbox_enqueued event_type=HH_UNMATCHED hh_vacancy_id={}",
                    hhVacancyId);
        } catch (DataIntegrityViolationException e) {
            log.debug("notification_outbox_duplicate dedupe_key={}", dedupeKey);
        }
    }

    public record HhScoreNotificationPayload(
            UUID vacancyId, String vacancyTitle, double score, double scoreLow, double scoreHigh) {}

    public record HhUnmatchedPayload(String hhVacancyId) {}
}
