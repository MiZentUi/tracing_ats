package ats.resume.infrastructure.hh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ats.gateway.model.HhWebhookEvent;
import ats.gateway.model.HhWebhookPayload;
import ats.resume.config.HhProperties;
import ats.resume.domain.ingestion.UploadOutcome;
import ats.resume.infrastructure.ingestion.ResumeIngestionService;
import ats.resume.infrastructure.notification.NotificationOutboxService;
import ats.resume.infrastructure.persistence.entity.HhWebhookEventEntity;
import ats.resume.infrastructure.persistence.repository.ApplicationRepository;
import ats.resume.infrastructure.persistence.repository.HhWebhookEventRepository;
import ats.vacancy.application.ApiException;
import ats.vacancy.hh.HhApiException;
import ats.vacancy.infrastructure.persistence.VacancyEntity;
import ats.vacancy.infrastructure.persistence.VacancyHhPublicationEntity;
import ats.vacancy.infrastructure.persistence.VacancyHhPublicationRepository;
import ats.vacancy.infrastructure.persistence.VacancyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;

class HhWebhookServiceTest {

    private static final String TOKEN = "mock-hh-token";

    private final HhProperties properties = new HhProperties();
    private final HhResumeClient resumeClient = mock(HhResumeClient.class);
    private final ResumeIngestionService ingestionService = mock(ResumeIngestionService.class);
    private final HhWebhookEventRepository eventRepository = mock(HhWebhookEventRepository.class);
    private final VacancyHhPublicationRepository publicationRepository =
            mock(VacancyHhPublicationRepository.class);
    private final VacancyRepository vacancyRepository = mock(VacancyRepository.class);
    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final NotificationOutboxService outboxService = mock(NotificationOutboxService.class);
    private final TaskExecutor executor = mock(TaskExecutor.class);

    private HhWebhookService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setToken("api-token");
        properties.setWebhookToken(TOKEN);
        properties.setWebhookPublicUrl("http://localhost/webhook?token=" + TOKEN);
        service =
                new HhWebhookService(
                        properties,
                        resumeClient,
                        ingestionService,
                        eventRepository,
                        publicationRepository,
                        vacancyRepository,
                        applicationRepository,
                        outboxService,
                        executor);
        when(eventRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            HhWebhookEventEntity entity = invocation.getArgument(0);
                            if (entity.getId() == null) {
                                entity.setId(UUID.randomUUID());
                            }
                            return entity;
                        });
        doAnswer(
                        invocation -> {
                            Runnable runnable = invocation.getArgument(0);
                            runnable.run();
                            return null;
                        })
                .when(executor)
                .execute(any());
    }

    @Test
    void rejectsWrongToken() {
        assertThatThrownBy(() -> service.receive("wrong", event("e-1", "vacancy-1", "resume-1")))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        error ->
                                assertThat(((ApiException) error).status())
                                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void rejectsWrongEmployer() {
        properties.setEmployerId("999");
        HhWebhookEvent event = event("e-1", "vacancy-1", "resume-1");
        event.getPayload().setEmployerId("111");

        assertThatThrownBy(() -> service.receive(TOKEN, event))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        error ->
                                assertThat(((ApiException) error).status())
                                        .isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(eventRepository, never()).save(any());
    }

    @Test
    void duplicateEventIsIgnored() {
        when(eventRepository.findByExternalEventId("e-1"))
                .thenReturn(Optional.of(new HhWebhookEventEntity()));

        service.receive(TOKEN, event("e-1", "vacancy-1", "resume-1"));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void unmatchedVacancyIsStoredAndAlerted() {
        HhWebhookEventEntity entity = eventEntity("e-1", "vacancy-1", "resume-1");
        when(eventRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(publicationRepository.findByHhVacancyId("vacancy-1")).thenReturn(Optional.empty());

        service.process(entity.getId());

        assertThat(entity.getStatus()).isEqualTo("UNMATCHED");
        assertThat(entity.getErrorCode()).isEqualTo("HH_VACANCY_NOT_LINKED");
        verify(outboxService).enqueueHhUnmatched("vacancy-1");
    }

    @Test
    void createsApplicationAndMarksEventProcessed() {
        HhWebhookEventEntity entity = eventEntity("e-1", "vacancy-1", "resume-1");
        UUID applicationId = UUID.randomUUID();
        when(eventRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(applicationRepository.findBySourceAndExternalRef("HH", "topic-1"))
                .thenReturn(Optional.empty());
        when(publicationRepository.findByHhVacancyId("vacancy-1"))
                .thenReturn(Optional.of(publication("vacancy-1", "PUBLISHED")));
        when(vacancyRepository.findById(any())).thenReturn(Optional.of(activeVacancy()));
        when(resumeClient.fetch("resume-1", "topic-1"))
                .thenReturn(new HhResume("resume-1", "Java-разработчик", "Java Spring Boot"));
        when(ingestionService.ingestExternal(any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        new UploadOutcome(
                                UUID.randomUUID(),
                                UploadOutcome.Status.MASKED,
                                List.of(),
                                null,
                                applicationId));

        service.process(entity.getId());

        assertThat(entity.getStatus()).isEqualTo("PROCESSED");
        assertThat(entity.getApplicationId()).isEqualTo(applicationId);
        verify(ingestionService).ingestExternal(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resumeFetchFailureMarksEventFailed() {
        HhWebhookEventEntity entity = eventEntity("e-1", "vacancy-1", "resume-1");
        when(eventRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(publicationRepository.findByHhVacancyId("vacancy-1"))
                .thenReturn(Optional.of(publication("vacancy-1", "PUBLISHED")));
        when(vacancyRepository.findById(any())).thenReturn(Optional.of(activeVacancy()));
        when(resumeClient.fetch("resume-1", "topic-1"))
                .thenThrow(
                        new HhApiException(
                                "HH_RESUME_FORBIDDEN", HttpStatus.FORBIDDEN, false, "forbidden"));

        service.process(entity.getId());

        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getErrorCode()).isEqualTo("HH_RESUME_FORBIDDEN");
    }

    private HhWebhookEvent event(String eventId, String vacancyId, String resumeId) {
        HhWebhookPayload payload = new HhWebhookPayload();
        payload.setVacancyId(vacancyId);
        payload.setResumeId(resumeId);
        payload.setTopicId("topic-1");
        payload.setEmployerId("999");
        payload.setNegotiationDate("2026-11-13T12:00:00+0300");
        HhWebhookEvent event = new HhWebhookEvent();
        event.setId(eventId);
        event.setSubscriptionId("sub-1");
        event.setActionType("NEW_NEGOTIATION_VACANCY");
        event.setPayload(payload);
        return event;
    }

    private HhWebhookEventEntity eventEntity(String eventId, String vacancyId, String resumeId) {
        HhWebhookEventEntity entity = new HhWebhookEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setExternalEventId(eventId);
        entity.setActionType("NEW_NEGOTIATION_VACANCY");
        entity.setHhVacancyId(vacancyId);
        entity.setHhResumeId(resumeId);
        entity.setHhTopicId("topic-1");
        entity.setStatus("RECEIVED");
        return entity;
    }

    private VacancyHhPublicationEntity publication(String hhVacancyId, String status) {
        VacancyHhPublicationEntity publication = new VacancyHhPublicationEntity();
        publication.setVacancyId(UUID.randomUUID());
        publication.setHhVacancyId(hhVacancyId);
        publication.setStatus(status);
        return publication;
    }

    private VacancyEntity activeVacancy() {
        return VacancyEntity.builder()
                .id(UUID.randomUUID())
                .title("Backend")
                .status("ACTIVE")
                .sourceType("TEXT")
                .sourceText("Java")
                .currentVersion(1)
                .build();
    }
}
