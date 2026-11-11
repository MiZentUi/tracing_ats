package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.RequirementResultEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementResultRepository extends JpaRepository<RequirementResultEntity, UUID> {

    List<RequirementResultEntity> findByScoreRunIdOrderByRequirementId(UUID scoreRunId);
}
