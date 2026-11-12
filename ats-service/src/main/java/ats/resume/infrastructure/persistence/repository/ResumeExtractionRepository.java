package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ResumeExtractionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeExtractionRepository extends JpaRepository<ResumeExtractionEntity, UUID> {

    Optional<ResumeExtractionEntity> findFirstByResumeIdOrderByCreatedAtDesc(UUID resumeId);
}
