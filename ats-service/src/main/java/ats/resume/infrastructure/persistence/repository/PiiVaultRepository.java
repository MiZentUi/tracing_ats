package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.PiiVaultEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PiiVaultRepository extends JpaRepository<PiiVaultEntity, UUID> {

    Optional<PiiVaultEntity> findByResumeId(UUID resumeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PiiVaultEntity p where p.resumeId = :resumeId")
    void deleteByResumeId(@Param("resumeId") UUID resumeId);
}
