package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ScoreRunEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreRunRepository extends JpaRepository<ScoreRunEntity, UUID> {

    Optional<ScoreRunEntity> findFirstByApplicationIdAndPhaseOrderByCreatedAtDesc(
            UUID applicationId, String phase);

    List<ScoreRunEntity> findByApplicationIdInAndPhaseOrderByCreatedAtDesc(
            Collection<UUID> applicationIds, String phase);

    List<ScoreRunEntity> findByResumeIdAndDirectionAndPhaseOrderByCreatedAtDesc(
            UUID resumeId, String direction, String phase);

    Optional<ScoreRunEntity>
            findFirstByResumeIdAndVacancyIdAndDirectionAndPhaseOrderByCreatedAtDesc(
                    UUID resumeId, UUID vacancyId, String direction, String phase);
}
