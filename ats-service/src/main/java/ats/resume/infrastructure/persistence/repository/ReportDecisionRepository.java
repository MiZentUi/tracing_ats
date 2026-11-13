package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ReportDecisionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportDecisionRepository extends JpaRepository<ReportDecisionEntity, UUID> {

    Optional<ReportDecisionEntity> findByResumeIdAndVacancyId(UUID resumeId, UUID vacancyId);
}
