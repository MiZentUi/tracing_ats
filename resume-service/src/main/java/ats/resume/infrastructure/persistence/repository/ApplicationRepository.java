package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    List<ApplicationEntity> findByVacancyId(UUID vacancyId);

    List<ApplicationEntity> findByVacancyIdAndStatus(UUID vacancyId, String status);

    List<ApplicationEntity> findByVacancyIdAndIdIn(UUID vacancyId, Collection<UUID> ids);
}
