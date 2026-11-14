package ats.resume.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ats.resume.config.TelegramProperties;
import ats.resume.infrastructure.json.JsonCodec;
import ats.resume.infrastructure.persistence.entity.NotificationOutboxEntity;
import ats.resume.infrastructure.persistence.repository.NotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationOutboxDispatcherTest {

    private final NotificationOutboxRepository repository =
            mock(NotificationOutboxRepository.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final TelegramProperties properties = new TelegramProperties();
    private final JsonCodec jsonCodec = new JsonCodec();

    @Test
    void sendsPendingNotificationAndMarksItSent() {
        properties.setEnabled(true);
        properties.setBotToken("token");
        properties.setChatId("1");
        NotificationOutboxEntity entity = scoredEntity();
        when(repository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        anyString(), any()))
                .thenReturn(List.of(entity));
        NotificationOutboxDispatcher dispatcher = dispatcher();

        dispatcher.dispatch();

        verify(sender).send(anyString());
        assertThat(entity.getStatus()).isEqualTo("SENT");
        assertThat(entity.getSentAt()).isNotNull();
    }

    @Test
    void retriesTransientFailureAndKeepsPending() {
        properties.setEnabled(true);
        properties.setBotToken("token");
        properties.setChatId("1");
        properties.setMaxAttempts(3);
        properties.setRetryDelay(java.time.Duration.ofSeconds(30));
        NotificationOutboxEntity entity = scoredEntity();
        when(repository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        anyString(), any()))
                .thenReturn(List.of(entity));
        doThrow(new NotificationException(true, "temporary")).when(sender).send(anyString());
        NotificationOutboxDispatcher dispatcher = dispatcher();

        dispatcher.dispatch();

        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getAttempts()).isEqualTo(1);
        assertThat(entity.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void marksFailedAfterMaxAttempts() {
        properties.setEnabled(true);
        properties.setBotToken("token");
        properties.setChatId("1");
        properties.setMaxAttempts(1);
        NotificationOutboxEntity entity = scoredEntity();
        when(repository.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        anyString(), any()))
                .thenReturn(List.of(entity));
        doThrow(new NotificationException(true, "temporary")).when(sender).send(anyString());
        NotificationOutboxDispatcher dispatcher = dispatcher();

        dispatcher.dispatch();

        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getAttempts()).isEqualTo(1);
    }

    private NotificationOutboxDispatcher dispatcher() {
        return new NotificationOutboxDispatcher(
                repository,
                sender,
                new HhNotificationMessageFactory(properties),
                new TelegramPropertiesProvider(properties),
                jsonCodec);
    }

    private NotificationOutboxEntity scoredEntity() {
        NotificationOutboxEntity entity = new NotificationOutboxEntity();
        entity.setId(UUID.randomUUID());
        entity.setDedupeKey("HH_SCORED:1");
        entity.setEventType("HH_SCORED");
        entity.setApplicationId(UUID.randomUUID());
        entity.setVacancyId(UUID.randomUUID());
        entity.setPayload(
                jsonCodec.write(
                        new NotificationOutboxService.HhScoreNotificationPayload(
                                entity.getVacancyId(), "Backend", 69, 62, 82)));
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        entity.setNextAttemptAt(Instant.now().minusSeconds(1));
        entity.setCreatedAt(Instant.now());
        return entity;
    }
}
