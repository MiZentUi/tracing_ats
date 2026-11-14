package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.CandidateEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<CandidateEntity, UUID> {

    Optional<CandidateEntity> findBySourceAndExternalRef(String source, String externalRef);
}
