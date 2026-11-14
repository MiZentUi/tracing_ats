package ats.vacancy.hh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ats.resume.config.HhProperties;
import ats.vacancy.application.ApiException;
import ats.vacancy.hh.HhApiClient.HhVacancy;
import ats.vacancy.hh.HhApiClient.HhVacancyDraft;
import ats.vacancy.infrastructure.persistence.HhSubscriptionRepository;
import ats.vacancy.infrastructure.persistence.TemplateVersionEntity;
import ats.vacancy.infrastructure.persistence.TemplateVersionRepository;
import ats.vacancy.infrastructure.persistence.VacancyEntity;
import ats.vacancy.infrastructure.persistence.VacancyHhPublicationRepository;
import ats.vacancy.infrastructure.persistence.VacancyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HhPublicationServiceTest {

    private final HhProperties properties = new HhProperties();
    private final HhApiClient hhApiClient = mock(HhApiClient.class);
    private final VacancyRepository vacancyRepository = mock(VacancyRepository.class);
    private final TemplateVersionRepository templateVersionRepository =
            mock(TemplateVersionRepository.class);
    private final VacancyHhPublicationRepository publicationRepository =
            mock(VacancyHhPublicationRepository.class);
    private final HhSubscriptionRepository subscriptionRepository =
            mock(HhSubscriptionRepository.class);

    private HhPublicationService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setToken("api-token");
        properties.setWebhookToken("webhook-token");
        properties.setWebhookPublicUrl("http://localhost/webhook?token=webhook-token");
        properties.setEmployerId("999");
        service =
                new HhPublicationService(
                        properties,
                        hhApiClient,
                        vacancyRepository,
                        templateVersionRepository,
                        publicationRepository,
                        subscriptionRepository);
        when(publicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishesDraftAndCreatesSubscription() {
        UUID vacancyId = UUID.randomUUID();
        when(vacancyRepository.findById(vacancyId))
                .thenReturn(Optional.of(activeVacancy(vacancyId)));
        when(templateVersionRepository.findFirstByVacancyIdAndConfirmedTrueOrderByVersionNoDesc(
                        vacancyId))
                .thenReturn(Optional.of(new TemplateVersionEntity()));
        when(publicationRepository.findById(vacancyId)).thenReturn(Optional.empty());
        when(hhApiClient.createDraft(any(HhVacancyDraft.class))).thenReturn("draft-1");
        when(hhApiClient.publishDraft("draft-1")).thenReturn(List.of("hh-1"));
        when(hhApiClient.getVacancy("hh-1"))
                .thenReturn(
                        new HhVacancy(
                                "hh-1", "Backend", "https://hh.ru/vacancy/hh-1", "999", false));
        when(subscriptionRepository.findFirstByStatusOrderByCreatedAtDesc("ACTIVE"))
                .thenReturn(Optional.empty());
        when(hhApiClient.createSubscription(anyString(), anyString())).thenReturn("sub-1");

        HhPublicationView result = service.publish(vacancyId);

        assertThat(result.status()).isEqualTo(HhPublicationView.Status.PUBLISHED);
        assertThat(result.hhVacancyId()).isEqualTo("hh-1");
        assertThat(result.hhUrl()).isEqualTo("https://hh.ru/vacancy/hh-1");
        verify(hhApiClient).createDraft(any(HhVacancyDraft.class));
        verify(hhApiClient).publishDraft("draft-1");
        verify(hhApiClient).createSubscription(anyString(), anyString());
    }

    @Test
    void rejectsInactiveVacancyBeforeCallingHh() {
        UUID vacancyId = UUID.randomUUID();
        VacancyEntity vacancy = activeVacancy(vacancyId);
        vacancy.setStatus("DRAFT");
        when(vacancyRepository.findById(vacancyId)).thenReturn(Optional.of(vacancy));

        assertThatThrownBy(() -> service.publish(vacancyId))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        error ->
                                assertThat(((ApiException) error).status())
                                        .isEqualTo(HttpStatus.CONFLICT));
        verify(hhApiClient, never()).createDraft(any());
    }

    private VacancyEntity activeVacancy(UUID id) {
        return VacancyEntity.builder()
                .id(id)
                .title("Backend")
                .status("ACTIVE")
                .sourceType("TEXT")
                .sourceText("Java")
                .currentVersion(1)
                .build();
    }
}
