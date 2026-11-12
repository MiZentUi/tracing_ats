package ats.resume.infrastructure.vacancy;

import ats.resume.domain.vacancy.VacancyCatalog;
import ats.resume.domain.vacancy.VacancyClient;
import ats.resume.domain.vacancy.VacancyTemplate;
import ats.vacancy.infrastructure.persistence.VacancyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Список вакансий берётся из таблицы в том же процессе, шаблон — через существующий gRPC-фасад. Так
 * обратный режим видит ровно те требования и версии, что и прямой.
 */
@Component
@RequiredArgsConstructor
public class GrpcVacancyCatalog implements VacancyCatalog {

    private final VacancyRepository vacancyRepository;
    private final VacancyClient vacancyClient;

    @Override
    public List<VacancyTemplate> listAll() {
        return vacancyRepository.findAllIds().stream().map(vacancyClient::getTemplate).toList();
    }
}
