package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ParsedResumeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParsedResumeRepository extends JpaRepository<ParsedResumeEntity, UUID> {

    Optional<ParsedResumeEntity> findByResumeId(UUID resumeId);
}
