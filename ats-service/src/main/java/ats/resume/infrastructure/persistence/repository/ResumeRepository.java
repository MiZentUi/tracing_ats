package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ResumeEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<ResumeEntity, UUID> {

    Page<ResumeEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
