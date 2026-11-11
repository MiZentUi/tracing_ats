package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.CandidateEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<CandidateEntity, UUID> {}
