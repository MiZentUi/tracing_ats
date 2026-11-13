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

/** Входящее событие hh.ru без тела запроса: только идентификаторы и статус. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "hh_webhook_event")
public class HhWebhookEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_event_id", nullable = false, length = 128)
    private String externalEventId;

    @Column(name = "subscription_id", length = 64)
    private String subscriptionId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "hh_vacancy_id", length = 64)
    private String hhVacancyId;

    @Column(name = "hh_resume_id", length = 64)
    private String hhResumeId;

    @Column(name = "hh_topic_id", length = 128)
    private String hhTopicId;

    @Column(name = "hh_employer_id", length = 64)
    private String hhEmployerId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
