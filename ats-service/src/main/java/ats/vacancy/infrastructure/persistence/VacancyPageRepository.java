package ats.vacancy.infrastructure.persistence;

import java.util.List;

/** Постраничная выборка вакансий с точным offset (контракт API задаёт limit/offset). */
public interface VacancyPageRepository {

    List<VacancyEntity> findPage(int limit, int offset);
}
