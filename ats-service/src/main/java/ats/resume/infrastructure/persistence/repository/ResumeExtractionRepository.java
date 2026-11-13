package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ResumeExtractionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeExtractionRepository extends JpaRepository<ResumeExtractionEntity, UUID> {

    Optional<ResumeExtractionEntity> findFirstByResumeIdOrderByCreatedAtDesc(UUID resumeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ResumeExtractionEntity e where e.resumeId = :resumeId")
    void deleteByResumeId(@Param("resumeId") UUID resumeId);
}
