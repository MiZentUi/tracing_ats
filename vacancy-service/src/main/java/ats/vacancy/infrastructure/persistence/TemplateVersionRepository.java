package ats.vacancy.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersionEntity, UUID> {

    Optional<TemplateVersionEntity> findByVacancyIdAndVersionNo(UUID vacancyId, int versionNo);

    Optional<TemplateVersionEntity> findFirstByVacancyIdAndConfirmedTrueOrderByVersionNoDesc(
            UUID vacancyId);

    Optional<TemplateVersionEntity> findFirstByVacancyIdOrderByVersionNoDesc(UUID vacancyId);

    List<TemplateVersionEntity> findByVacancyIdIn(Collection<UUID> vacancyIds);
}
