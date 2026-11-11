package ats.vacancy.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRepository extends JpaRepository<RequirementEntity, UUID> {

    List<RequirementEntity> findByTemplateVersionIdOrderByPosition(UUID templateVersionId);

    List<RequirementEntity> findByTemplateVersionIdIn(Collection<UUID> templateVersionIds);

    long countByTemplateVersionId(UUID templateVersionId);
}
