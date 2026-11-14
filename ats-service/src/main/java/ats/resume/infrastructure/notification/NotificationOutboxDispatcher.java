package ats.resume.infrastructure.notification;

import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.NotificationOutboxEntity;
import ats.resume.infrastructure.persistence.repository.NotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Фоновый поллер outbox: retry с задержкой, после max attempts — FAILED. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxDispatcher {

    private final NotificationOutboxRepository repository;
    private final NotificationSender sender;
    private final HhNotificationMessageFactory messageFactory;
    private final TelegramPropertiesProvider properties;
    private final JsonCodec jsonCodec;

    @Scheduled(fixedDelayString = "${ats.telegram.poll-interval:5s}")
    public void dispatch() {
        List<NotificationOutboxEntity> pending =
                repository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        "PENDING", Instant.now());
        for (NotificationOutboxEntity entity : pending) {
            sendOne(entity);
        }
    }

    private void sendOne(NotificationOutboxEntity entity) {
        if (!properties.enabled()) {
            entity.setStatus("SKIPPED");
            repository.save(entity);
            return;
        }
        try {
            sender.send(message(entity));
            entity.setStatus("SENT");
            entity.setSentAt(Instant.now());
            entity.setLastError(null);
            repository.save(entity);
            log.info(
                    "notification_outbox_sent event_type={} application_id={}",
                    entity.getEventType(),
                    entity.getApplicationId());
        } catch (NotificationException e) {
            int attempts = entity.getAttempts() + 1;
            entity.setAttempts(attempts);
            entity.setLastError(shortError(e.getMessage()));
            if (!e.retryable() || attempts >= properties.maxAttempts()) {
                entity.setStatus("FAILED");
                log.warn(
                        "notification_outbox_failed event_type={} application_id={}"
                                + " attempts={} retryable={}",
                        entity.getEventType(),
                        entity.getApplicationId(),
                        attempts,
                        e.retryable());
            } else {
                entity.setNextAttemptAt(
                        Instant.now().plus(properties.retryDelay().multipliedBy(attempts)));
                log.info(
                        "notification_outbox_retry event_type={} application_id={} attempts={}",
                        entity.getEventType(),
                        entity.getApplicationId(),
                        attempts);
            }
            repository.save(entity);
        }
    }

    private String message(NotificationOutboxEntity entity) {
        return switch (entity.getEventType()) {
            case "HH_SCORED" ->
                    messageFactory.scored(
                            jsonCodec.read(
                                    entity.getPayload(),
                                    NotificationOutboxService.HhScoreNotificationPayload.class),
                            entity.getApplicationId());
            case "HH_UNMATCHED" ->
                    messageFactory.unmatched(
                            jsonCodec.read(
                                    entity.getPayload(),
                                    NotificationOutboxService.HhUnmatchedPayload.class));
            default -> throw new NotificationException(false, "unknown notification event type");
        };
    }

    private static String shortError(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 128 ? value.substring(0, 128) : value;
    }
}
