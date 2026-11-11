package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.AuditEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {}
