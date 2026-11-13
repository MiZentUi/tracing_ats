package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParsedResumeRepository extends JpaRepository<ParsedResumeEntity, UUID> {

    Optional<ParsedResumeEntity> findByResumeId(UUID resumeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ParsedResumeEntity p where p.resumeId = :resumeId")
    void deleteByResumeId(@Param("resumeId") UUID resumeId);
}
