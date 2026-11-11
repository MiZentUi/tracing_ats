package ats.vacancy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringWeightsRepository extends JpaRepository<ScoringWeightsEntity, UUID> {

    Optional<ScoringWeightsEntity> findFirstByStatus(String status);
}
