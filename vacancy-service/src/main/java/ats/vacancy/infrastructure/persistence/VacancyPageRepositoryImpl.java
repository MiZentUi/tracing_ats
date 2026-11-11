package ats.vacancy.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * JPQL-фрагмент вместо native query: API принимает limit/offset, а Spring Data Pageable работает
 * номерами страниц и с произвольным offset даёт другую выборку.
 */
@Repository
@RequiredArgsConstructor
public class VacancyPageRepositoryImpl implements VacancyPageRepository {

    private final EntityManager entityManager;

    @Override
    public List<VacancyEntity> findPage(int limit, int offset) {
        return entityManager
                .createQuery(
                        "select v from VacancyEntity v order by v.createdAt desc",
                        VacancyEntity.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
