package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ScreeningBatchEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningBatchRepository extends JpaRepository<ScreeningBatchEntity, UUID> {

    List<ScreeningBatchEntity> findByVacancyIdOrderByCreatedAtDesc(UUID vacancyId);
}
