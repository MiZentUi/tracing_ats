package ats.vacancy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HhSubscriptionRepository extends JpaRepository<HhSubscriptionEntity, UUID> {

    Optional<HhSubscriptionEntity> findFirstByStatusOrderByCreatedAtDesc(String status);
}
