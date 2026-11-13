package ats.resume.infrastructure.persistence.repository;

import ats.resume.infrastructure.persistence.entity.ApplicationEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<ApplicationEntity, UUID> {

    List<ApplicationEntity> findByVacancyId(UUID vacancyId);

    List<ApplicationEntity> findByResumeId(UUID resumeId);

    List<ApplicationEntity> findByVacancyIdAndStatus(UUID vacancyId, String status);

    List<ApplicationEntity> findByVacancyIdAndStatusIn(UUID vacancyId, Collection<String> statuses);

    List<ApplicationEntity> findByVacancyIdAndIdIn(UUID vacancyId, Collection<UUID> ids);

    /**
     * Атомарно забирает отклик в скоринг. Если его уже забрал другой worker или статус не готов,
     * обновляется ноль строк — повторный вызов не стартует.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update ApplicationEntity a
               set a.status = 'SCORING', a.updatedAt = CURRENT_TIMESTAMP
             where a.id = :id and a.status in ('MASKED', 'SCORED')
            """)
    int markScoring(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update ApplicationEntity a
               set a.status = :status, a.updatedAt = CURRENT_TIMESTAMP
             where a.id = :id
            """)
    int updateStatus(@Param("id") UUID id, @Param("status") String status);
}
