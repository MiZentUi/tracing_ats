package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.PiiVaultEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PiiVaultRepository extends JpaRepository<PiiVaultEntity, UUID> {

    Optional<PiiVaultEntity> findByResumeId(UUID resumeId);
}
