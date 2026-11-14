package ats.vacancy.hh;

import java.util.List;

/** Минимальный контракт hh.ru, нужный для публикации вакансии и подписки на отклики. */
public interface HhApiClient {

    String createDraft(HhVacancyDraft draft);

    void updateDraft(String draftId, HhVacancyDraft draft);

    List<String> publishDraft(String draftId);

    HhVacancy getVacancy(String vacancyId);

    String createSubscription(String url, String employerId);

    java.util.List<HhSubscription> listSubscriptions();

    void deleteSubscription(String subscriptionId);

    void archiveVacancy(String vacancyId);

    record HhVacancyDraft(String name, String description) {}

    record HhVacancy(
            String id, String name, String alternateUrl, String employerId, boolean archived) {}

    record HhSubscription(String id, String url) {}
}
