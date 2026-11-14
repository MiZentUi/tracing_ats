package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.CandidateMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateMessageRepository extends JpaRepository<CandidateMessageEntity, UUID> {

    List<CandidateMessageEntity> findByScoreRunIdOrderByCreatedAtDesc(UUID scoreRunId);
}
