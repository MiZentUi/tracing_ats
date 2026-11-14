package ats.vacancy.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacancyHhPublicationRepository
        extends JpaRepository<VacancyHhPublicationEntity, UUID> {

    Optional<VacancyHhPublicationEntity> findByHhVacancyId(String hhVacancyId);

    Optional<VacancyHhPublicationEntity> findByVacancyIdAndStatus(UUID vacancyId, String status);
}
