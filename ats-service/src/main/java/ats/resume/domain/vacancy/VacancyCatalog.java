package ats.resume.domain.vacancy;

import java.util.List;

/** Каталог всех шаблонов вакансий для направления «вакансии под резюме». */
public interface VacancyCatalog {

    List<VacancyTemplate> listAll();
}
