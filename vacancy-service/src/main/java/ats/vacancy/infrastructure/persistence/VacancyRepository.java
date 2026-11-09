package ats.vacancy.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacancyRepository extends JpaRepository<VacancyEntity, UUID> {

    @Query(
            value = "select * from vacancy order by created_at desc limit :limit offset :offset",
            nativeQuery = true)
    List<VacancyEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VacancyEntity v where v.id = :id")
    Optional<VacancyEntity> findByIdForUpdate(@Param("id") UUID id);
}
