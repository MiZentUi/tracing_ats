package ats.vacancy.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacancyRepository
        extends JpaRepository<VacancyEntity, UUID>, VacancyPageRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VacancyEntity v where v.id = :id")
    Optional<VacancyEntity> findByIdForUpdate(@Param("id") UUID id);

    /** Идентификаторы всех вакансий: обратный режим сравнивает резюме со всеми. */
    @Query("select v.id from VacancyEntity v")
    java.util.List<UUID> findAllIds();
}
