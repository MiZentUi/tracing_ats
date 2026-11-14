package ats.vacancy.hh;

import ats.resume.config.HhProperties;
import ats.vacancy.application.ApiException;
import ats.vacancy.hh.HhApiClient.HhVacancy;
import ats.vacancy.hh.HhApiClient.HhVacancyDraft;
import ats.vacancy.infrastructure.persistence.HhSubscriptionEntity;
import ats.vacancy.infrastructure.persistence.HhSubscriptionRepository;
import ats.vacancy.infrastructure.persistence.TemplateVersionRepository;
import ats.vacancy.infrastructure.persistence.VacancyEntity;
import ats.vacancy.infrastructure.persistence.VacancyHhPublicationEntity;
import ats.vacancy.infrastructure.persistence.VacancyHhPublicationRepository;
import ats.vacancy.infrastructure.persistence.VacancyRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Публикация вакансии на hh.ru: черновик → публикация → привязка → подписка на отклики. Повторный
 * вызов использует сохранённый черновик и не создаёт дубль.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HhPublicationService {

    private final HhProperties properties;
    private final HhApiClient hhApiClient;
    private final VacancyRepository vacancyRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final VacancyHhPublicationRepository publicationRepository;
    private final HhSubscriptionRepository subscriptionRepository;

    public HhPublicationView get(UUID vacancyId) {
        requireVacancy(vacancyId);
        return publicationRepository
                .findById(vacancyId)
                .map(this::refreshMockLink)
                .map(this::view)
                .orElseGet(
                        () ->
                                new HhPublicationView(
                                        vacancyId,
                                        HhPublicationView.Status.NOT_PUBLISHED,
                                        null,
                                        null,
                                        null,
                                        null));
    }

    /**
     * В mock-режиме ссылка на вакансию могла быть сохранена до появления mock-frontend. Обновляем
     * её при чтении, чтобы «Открыть на hh.ru» вела в mock, а не на реальный сайт.
     */
    private VacancyHhPublicationEntity refreshMockLink(VacancyHhPublicationEntity publication) {
        if (!"PUBLISHED".equals(publication.getStatus())
                || publication.getHhVacancyId() == null
                || "https://api.hh.ru".equals(properties.getApiBaseUrl())) {
            return publication;
        }
        try {
            String alternateUrl =
                    hhApiClient.getVacancy(publication.getHhVacancyId()).alternateUrl();
            if (alternateUrl != null && !alternateUrl.equals(publication.getHhUrl())) {
                publication.setHhUrl(alternateUrl);
                publication.setUpdatedAt(Instant.now());
                publicationRepository.save(publication);
            }
        } catch (HhApiException e) {
            if ("HH_NOT_FOUND".equals(e.errorCode())) {
                // mock-hh перезапустили без сохранения состояния: привязка устарела.
                publication.setStatus("FAILED");
                publication.setErrorCode("HH_VACANCY_LOST");
                publication.setHhVacancyId(null);
                publication.setHhDraftId(null);
                publication.setHhUrl(null);
                publication.setPublishedAt(null);
                publication.setUpdatedAt(Instant.now());
                publicationRepository.save(publication);
                log.warn(
                        "hh_vacancy_lost_in_mock vacancy_id={} code={}",
                        publication.getVacancyId(),
                        e.errorCode());
                return publication;
            }
            log.warn(
                    "hh_vacancy_link_refresh_failed vacancy_id={} hh_vacancy_id={} code={}",
                    publication.getVacancyId(),
                    publication.getHhVacancyId(),
                    e.errorCode());
        }
        return publication;
    }

    public HhPublicationView publish(UUID vacancyId) {
        if (!properties.isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "HH_NOT_CONFIGURED",
                    "hh.ru integration is not configured");
        }
        VacancyEntity vacancy = requireVacancy(vacancyId);
        if (!"ACTIVE".equals(vacancy.getStatus())) {
            throw ApiException.conflict(
                    "VACANCY_NOT_ACTIVE", "Only an active vacancy can be published to hh.ru");
        }
        boolean confirmed =
                templateVersionRepository
                        .findFirstByVacancyIdAndConfirmedTrueOrderByVersionNoDesc(vacancyId)
                        .isPresent();
        if (!confirmed) {
            throw ApiException.unprocessable(
                    "TEMPLATE_NOT_CONFIRMED",
                    "Confirm the vacancy template before publishing to hh.ru");
        }

        VacancyHhPublicationEntity publication =
                publicationRepository
                        .findById(vacancyId)
                        .orElseGet(
                                () -> {
                                    VacancyHhPublicationEntity created =
                                            new VacancyHhPublicationEntity();
                                    created.setVacancyId(vacancyId);
                                    created.setStatus("PENDING");
                                    created.setRequestedAt(Instant.now());
                                    created.setUpdatedAt(Instant.now());
                                    return created;
                                });
        if ("PUBLISHED".equals(publication.getStatus())) {
            ensureSubscription();
            return view(publication);
        }
        publication.setStatus("PENDING");
        publication.setUpdatedAt(Instant.now());
        publication.setErrorCode(null);
        publication = publicationRepository.save(publication);

        long startedAt = System.nanoTime();
        log.info("hh_vacancy_publish_started vacancy_id={}", vacancyId);
        try {
            if (publication.getHhDraftId() == null || publication.getHhDraftId().isBlank()) {
                publication = createDraft(publication, vacancy);
            } else {
                try {
                    hhApiClient.updateDraft(
                            publication.getHhDraftId(),
                            new HhVacancyDraft(vacancy.getTitle(), vacancy.getSourceText()));
                } catch (HhApiException e) {
                    if ("HH_NOT_FOUND".equals(e.errorCode())) {
                        // mock-hh или hh потеряли черновик: создаём новый, а не падаем.
                        publication.setHhDraftId(null);
                        publication = createDraft(publication, vacancy);
                    } else {
                        throw e;
                    }
                }
            }

            List<String> publishedIds = hhApiClient.publishDraft(publication.getHhDraftId());
            String hhVacancyId = publishedIds.getFirst();
            publicationRepository
                    .findByHhVacancyId(hhVacancyId)
                    .filter(existing -> !existing.getVacancyId().equals(vacancyId))
                    .ifPresent(
                            existing -> {
                                throw new ApiException(
                                        HttpStatus.CONFLICT,
                                        "HH_VACANCY_ALREADY_LINKED",
                                        "hh.ru vacancy is already linked to another vacancy");
                            });
            publication.setHhVacancyId(hhVacancyId);
            publication.setStatus("PUBLISHED");
            publication.setPublishedAt(Instant.now());
            publication.setUpdatedAt(Instant.now());
            try {
                publication = publicationRepository.save(publication);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                publication.setHhVacancyId(null);
                publication.setStatus("FAILED");
                publication.setErrorCode("HH_VACANCY_ALREADY_LINKED");
                publication.setUpdatedAt(Instant.now());
                publicationRepository.save(publication);
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "HH_VACANCY_ALREADY_LINKED",
                        "hh.ru vacancy is already linked to another vacancy");
            }

            try {
                HhVacancy hhVacancy = hhApiClient.getVacancy(hhVacancyId);
                if (properties.getEmployerId() != null
                        && !properties.getEmployerId().isBlank()
                        && hhVacancy.employerId() != null
                        && !properties.getEmployerId().equals(hhVacancy.employerId())) {
                    log.warn(
                            "hh_vacancy_employer_mismatch vacancy_id={} hh_vacancy_id={}",
                            vacancyId,
                            hhVacancyId);
                }
                publication.setHhUrl(hhVacancy.alternateUrl());
                publication.setHhEmployerId(hhVacancy.employerId());
                publication.setUpdatedAt(Instant.now());
                publication = publicationRepository.save(publication);
            } catch (HhApiException e) {
                // Вакансия уже опубликована: не откатываем статус из-за метаданных.
                log.warn(
                        "hh_vacancy_metadata_failed vacancy_id={} hh_vacancy_id={} code={}",
                        vacancyId,
                        hhVacancyId,
                        e.errorCode());
            }

            ensureSubscription();
            log.info(
                    "hh_vacancy_published vacancy_id={} hh_vacancy_id={} elapsed_ms={}",
                    vacancyId,
                    hhVacancyId,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return view(publication);
        } catch (HhApiException e) {
            publication.setStatus("FAILED");
            publication.setErrorCode(e.errorCode());
            publication.setUpdatedAt(Instant.now());
            publicationRepository.save(publication);
            log.warn(
                    "hh_vacancy_publish_failed vacancy_id={} code={} status={} elapsed_ms={}",
                    vacancyId,
                    e.errorCode(),
                    e.status().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    e.errorCode(),
                    "hh.ru publication failed: " + e.errorCode());
        }
    }

    public HhPublicationView archive(UUID vacancyId) {
        requireVacancy(vacancyId);
        VacancyHhPublicationEntity publication =
                publicationRepository
                        .findById(vacancyId)
                        .orElseThrow(() -> ApiException.notFound("hh publication not found"));
        if ("PUBLISHED".equals(publication.getStatus()) && publication.getHhVacancyId() != null) {
            try {
                hhApiClient.archiveVacancy(publication.getHhVacancyId());
            } catch (HhApiException e) {
                log.warn(
                        "hh_vacancy_archive_failed vacancy_id={} hh_vacancy_id={} code={}",
                        vacancyId,
                        publication.getHhVacancyId(),
                        e.errorCode());
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        e.errorCode(),
                        "hh.ru archive failed: " + e.errorCode());
            }
        }
        publication.setStatus("ARCHIVED");
        publication.setUpdatedAt(Instant.now());
        publicationRepository.save(publication);
        log.info("hh_vacancy_archived vacancy_id={}", vacancyId);
        return view(publication);
    }

    private VacancyHhPublicationEntity createDraft(
            VacancyHhPublicationEntity publication, VacancyEntity vacancy) {
        String draftId =
                hhApiClient.createDraft(
                        new HhVacancyDraft(vacancy.getTitle(), vacancy.getSourceText()));
        publication.setHhDraftId(draftId);
        publication.setUpdatedAt(Instant.now());
        VacancyHhPublicationEntity saved = publicationRepository.save(publication);
        log.info("hh_vacancy_draft_created vacancy_id={} hh_draft_id={}", vacancy.getId(), draftId);
        return saved;
    }

    private void ensureSubscription() {
        if (subscriptionRepository.findFirstByStatusOrderByCreatedAtDesc("ACTIVE").isPresent()) {
            return;
        }
        String subscriptionId = findExistingSubscription();
        if (subscriptionId == null) {
            try {
                subscriptionId =
                        hhApiClient.createSubscription(
                                properties.getWebhookPublicUrl(), properties.getEmployerId());
            } catch (HhApiException e) {
                // Возможно, подписка уже создана в hh: пробуем найти её.
                subscriptionId = findExistingSubscription();
                if (subscriptionId == null) {
                    log.warn("hh_subscription_failed code={}", e.errorCode());
                    return;
                }
            }
        }
        HhSubscriptionEntity subscription = new HhSubscriptionEntity();
        subscription.setSubscriptionId(subscriptionId);
        subscription.setUrl(properties.getWebhookPublicUrl());
        subscription.setEmployerId(properties.getEmployerId());
        subscription.setStatus("ACTIVE");
        subscription.setCreatedAt(Instant.now());
        subscriptionRepository.save(subscription);
        log.info("hh_subscription_created subscription_id={}", subscriptionId);
    }

    private String findExistingSubscription() {
        try {
            return hhApiClient.listSubscriptions().stream()
                    .filter(
                            item ->
                                    properties.getWebhookPublicUrl().equals(item.url())
                                            && item.id() != null
                                            && !item.id().isBlank())
                    .map(HhApiClient.HhSubscription::id)
                    .findFirst()
                    .orElse(null);
        } catch (HhApiException e) {
            log.warn("hh_subscription_list_failed code={}", e.errorCode());
            return null;
        }
    }

    private VacancyEntity requireVacancy(UUID vacancyId) {
        return vacancyRepository
                .findById(vacancyId)
                .orElseThrow(() -> ApiException.notFound("Vacancy not found"));
    }

    private HhPublicationView view(VacancyHhPublicationEntity publication) {
        return new HhPublicationView(
                publication.getVacancyId(),
                HhPublicationView.Status.valueOf(publication.getStatus()),
                publication.getHhVacancyId(),
                publication.getHhUrl(),
                publication.getErrorCode(),
                publication.getPublishedAt());
    }
}
