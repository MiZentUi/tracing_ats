package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.NotificationOutboxEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository
        extends JpaRepository<NotificationOutboxEntity, UUID> {

    Optional<NotificationOutboxEntity> findByDedupeKey(String dedupeKey);

    List<NotificationOutboxEntity>
            findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                    String status, Instant now);
}
