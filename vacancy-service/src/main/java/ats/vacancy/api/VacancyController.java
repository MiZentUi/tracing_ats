package ats.vacancy.api;

import ats.api.VacanciesApi;
import ats.gateway.model.UpdateTemplateRequest;
import ats.gateway.model.Vacancy;
import ats.gateway.model.VacancyList;
import ats.vacancy.application.CreateVacancyCommand;
import ats.vacancy.application.VacancyService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class VacancyController implements VacanciesApi {

    private final VacancyService vacancyService;
    private final ApiMapper mapper;

    @Override
    public ResponseEntity<Vacancy> createVacancy(
            String specialization, String level, String title, String text, MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        mapper.toVacancy(
                                vacancyService.create(
                                        new CreateVacancyCommand(
                                                specialization, level, title, text, file))));
    }

    @Override
    public ResponseEntity<Vacancy> getVacancy(UUID id) {
        return ResponseEntity.ok(mapper.toVacancy(vacancyService.get(id)));
    }

    @Override
    public ResponseEntity<VacancyList> listVacancies(Integer limit, Integer offset) {
        return ResponseEntity.ok(mapper.toVacancyList(vacancyService.list(limit, offset)));
    }

    @Override
    public ResponseEntity<Vacancy> updateVacancyTemplate(
            UUID id, UpdateTemplateRequest updateTemplateRequest) {
        return ResponseEntity.ok(
                mapper.toVacancy(
                        vacancyService.update(id, mapper.toCommand(updateTemplateRequest))));
    }

    @Override
    public ResponseEntity<Void> deleteVacancy(UUID id) {
        vacancyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
