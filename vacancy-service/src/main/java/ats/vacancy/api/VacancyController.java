package ats.vacancy.api;

import ats.api.VacanciesApi;
import ats.gateway.model.UpdateTemplateRequest;
import ats.gateway.model.Vacancy;
import ats.gateway.model.VacancyList;
import ats.vacancy.api.mapping.CommandMapper;
import ats.vacancy.api.mapping.VacancyApiMapper;
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
    private final VacancyApiMapper vacancyMapper;
    private final CommandMapper commandMapper;

    @Override
    public ResponseEntity<Vacancy> createVacancy(
            String specialization, String level, String title, String text, MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        vacancyMapper.toVacancy(
                                vacancyService.create(
                                        new CreateVacancyCommand(
                                                specialization, level, title, text, file))));
    }

    @Override
    public ResponseEntity<Vacancy> getVacancy(UUID id) {
        return ResponseEntity.ok(vacancyMapper.toVacancy(vacancyService.get(id)));
    }

    @Override
    public ResponseEntity<VacancyList> listVacancies(Integer limit, Integer offset) {
        return ResponseEntity.ok(vacancyMapper.toVacancyList(vacancyService.list(limit, offset)));
    }

    @Override
    public ResponseEntity<Vacancy> updateVacancyTemplate(
            UUID id, UpdateTemplateRequest updateTemplateRequest) {
        return ResponseEntity.ok(
                vacancyMapper.toVacancy(
                        vacancyService.update(id, commandMapper.toCommand(updateTemplateRequest))));
    }

    @Override
    public ResponseEntity<Void> deleteVacancy(UUID id) {
        vacancyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
