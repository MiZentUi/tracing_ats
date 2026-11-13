package ats.resume.infrastructure.hh;

import ats.gateway.model.HhWebhookEvent;
import ats.gateway.model.HhWebhookPayload;
import ats.resume.config.HhProperties;
import ats.resume.domain.ingestion.UploadOutcome;
import ats.resume.infrastructure.ingestion.ResumeIngestionService;
import ats.resume.infrastructure.notification.NotificationOutboxService;
import ats.resume.infrastructure.persistence.entity.HhWebhookEventEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.HhWebhookEventRepository;
import ats.support.logging.RequestIds;
import ats.vacancy.application.ApiException;
import ats.vacancy.hh.HhApiException;
import ats.vacancy.infrastructure.persistence.VacancyEntity;
import ats.vacancy.infrastructure.persistence.VacancyHhPublicationEntity;
import ats.vacancy.infrastructure.persistence.VacancyHhPublicationRepository;
import ats.vacancy.infrastructure.persistence.VacancyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Приём и обработка вебхуков hh.ru. Ответ отдаётся сразу, обработка идёт в отдельном пуле. Тело
 * события не хранится и не логируется: только идентификаторы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HhWebhookService {

    private static final String NEW_NEGOTIATION = "NEW_NEGOTIATION_VACANCY";

    private final HhProperties properties;
    private final HhResumeClient resumeClient;
    private final ResumeIngestionService ingestionService;
    private final HhWebhookEventRepository eventRepository;
    private final VacancyHhPublicationRepository publicationRepository;
    private final VacancyRepository vacancyRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationOutboxService notificationOutboxService;

    @Qualifier("hhExecutor")
    private final TaskExecutor executor;

    public void receive(String token, HhWebhookEvent request) {
        if (!properties.isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "HH_NOT_CONFIGURED",
                    "hh.ru integration is not configured");
        }
        if (!constantTimeEquals(token, properties.getWebhookToken())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "HH_BAD_TOKEN", "Invalid webhook token");
        }
        if (properties.getSubscriptionId() != null
                && !properties.getSubscriptionId().isBlank()
                && !properties.getSubscriptionId().equals(request.getSubscriptionId())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "HH_BAD_SUBSCRIPTION", "Unknown subscription");
        }
        if (request.getPayload() != null
                && properties.getEmployerId() != null
                && !properties.getEmployerId().isBlank()
                && request.getPayload().getEmployerId() != null
                && !properties.getEmployerId().equals(request.getPayload().getEmployerId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "HH_BAD_EMPLOYER", "Unknown employer");
        }
        if (eventRepository.findByExternalEventId(request.getId()).isPresent()) {
            log.info("hh_webhook_duplicate event_id={}", request.getId());
            return;
        }

        HhWebhookPayload payload = request.getPayload();
        HhWebhookEventEntity event = new HhWebhookEventEntity();
        event.setExternalEventId(request.getId());
        event.setSubscriptionId(request.getSubscriptionId());
        event.setActionType(request.getActionType());
        event.setHhVacancyId(payload == null ? null : payload.getVacancyId());
        event.setHhResumeId(payload == null ? null : payload.getResumeId());
        event.setHhTopicId(payload == null ? null : payload.getTopicId());
        event.setHhEmployerId(payload == null ? null : payload.getEmployerId());
        event.setStatus(NEW_NEGOTIATION.equals(request.getActionType()) ? "RECEIVED" : "IGNORED");
        event.setErrorCode(
                NEW_NEGOTIATION.equals(request.getActionType()) ? null : "UNSUPPORTED_ACTION");
        event.setReceivedAt(Instant.now());
        if (!NEW_NEGOTIATION.equals(request.getActionType())) {
            event.setProcessedAt(Instant.now());
        }
        try {
            event = eventRepository.save(event);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("hh_webhook_duplicate event_id={}", request.getId());
            return;
        }
        log.info(
                "hh_webhook_received event_id={} action_type={} hh_vacancy_id={} hh_resume_id={}",
                request.getId(),
                request.getActionType(),
                event.getHhVacancyId(),
                event.getHhResumeId());

        if (!NEW_NEGOTIATION.equals(request.getActionType())) {
            return;
        }
        UUID eventId = event.getId();
        String requestId = RequestIds.current();
        try {
            executor.execute(
                    () -> {
                        RequestIds.put(requestId == null ? RequestIds.generate() : requestId);
                        try {
                            process(eventId);
                        } finally {
                            RequestIds.clear();
                        }
                    });
        } catch (RuntimeException e) {
            finish(event, "FAILED", "EXECUTOR_REJECTED");
            log.warn(
                    "hh_webhook_rejected event_id={} error={}",
                    request.getId(),
                    e.getClass().getSimpleName());
        }
    }

    void process(UUID eventId) {
        HhWebhookEventEntity event = eventRepository.findById(eventId).orElse(null);
        if (event == null || !"RECEIVED".equals(event.getStatus())) {
            return;
        }
        event.setStatus("PROCESSING");
        eventRepository.save(event);
        try {
            if (isBlank(event.getHhVacancyId()) || isBlank(event.getHhResumeId())) {
                finish(event, "IGNORED", "HH_PAYLOAD_INCOMPLETE");
                return;
            }
            var existingApplication =
                    applicationRepository.findBySourceAndExternalRef("HH", event.getHhTopicId());
            if (existingApplication.isPresent()) {
                event.setApplicationId(existingApplication.get().getId());
                finish(event, "PROCESSED", null);
                return;
            }
            VacancyHhPublicationEntity publication =
                    publicationRepository.findByHhVacancyId(event.getHhVacancyId()).orElse(null);
            if (publication == null || !"PUBLISHED".equals(publication.getStatus())) {
                finish(event, "UNMATCHED", "HH_VACANCY_NOT_LINKED");
                notificationOutboxService.enqueueHhUnmatched(event.getHhVacancyId());
                log.warn(
                        "hh_webhook_unmatched hh_vacancy_id={} event_id={}",
                        event.getHhVacancyId(),
                        event.getExternalEventId());
                return;
            }
            VacancyEntity vacancy =
                    vacancyRepository.findById(publication.getVacancyId()).orElse(null);
            if (vacancy == null || !"ACTIVE".equals(vacancy.getStatus())) {
                finish(event, "IGNORED", "VACANCY_NOT_ACTIVE");
                return;
            }

            HhResume resume;
            try {
                resume = resumeClient.fetch(event.getHhResumeId(), event.getHhTopicId());
            } catch (HhApiException e) {
                finish(event, "FAILED", e.errorCode());
                log.warn(
                        "hh_resume_fetch_failed event_id={} hh_vacancy_id={} code={} retryable={}",
                        event.getExternalEventId(),
                        event.getHhVacancyId(),
                        e.errorCode(),
                        e.retryable());
                return;
            }

            UploadOutcome outcome =
                    ingestionService.ingestExternal(
                            vacancy.getId(),
                            event.getHhTopicId(),
                            event.getHhResumeId(),
                            event.getHhResumeId(),
                            resume.title(),
                            resume.text());
            if (outcome.applicationId() == null) {
                finish(event, "FAILED", "APPLICATION_NOT_CREATED");
                return;
            }
            event.setApplicationId(outcome.applicationId());
            if (outcome.status() != UploadOutcome.Status.MASKED) {
                finish(event, "FAILED", outcome.status().name());
                return;
            }
            finish(event, "PROCESSED", null);
            log.info(
                    "hh_application_created event_id={} application_id={} vacancy_id={}",
                    event.getExternalEventId(),
                    outcome.applicationId(),
                    vacancy.getId());
        } catch (RuntimeException e) {
            finish(event, "FAILED", "PROCESSING_ERROR");
            log.error(
                    "hh_webhook_processing_failed event_id={} error={}",
                    event.getExternalEventId(),
                    e.getClass().getSimpleName());
        }
    }

    private void finish(HhWebhookEventEntity event, String status, String errorCode) {
        event.setStatus(status);
        event.setErrorCode(errorCode);
        event.setProcessedAt(Instant.now());
        eventRepository.save(event);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
