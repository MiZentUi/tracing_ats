package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ExternalEvidenceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalEvidenceRepository extends JpaRepository<ExternalEvidenceEntity, UUID> {

    List<ExternalEvidenceEntity> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    List<ExternalEvidenceEntity> findByScoreRunIdOrderByCreatedAtAsc(UUID scoreRunId);
}
