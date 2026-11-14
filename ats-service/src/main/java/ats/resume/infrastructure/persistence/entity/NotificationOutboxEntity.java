package ats.resume.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Очередь уведомлений: без PII, с дедупликацией по ключу. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_outbox")
public class NotificationOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dedupe_key", nullable = false, length = 160)
    private String dedupeKey;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "vacancy_id")
    private UUID vacancyId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 128)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
