package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.HhWebhookEventEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HhWebhookEventRepository extends JpaRepository<HhWebhookEventEntity, UUID> {

    Optional<HhWebhookEventEntity> findByExternalEventId(String externalEventId);

    List<HhWebhookEventEntity> findByHhVacancyIdAndStatusOrderByReceivedAtAsc(
            String hhVacancyId, String status);
}
