package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<ResumeEntity, UUID> {}
