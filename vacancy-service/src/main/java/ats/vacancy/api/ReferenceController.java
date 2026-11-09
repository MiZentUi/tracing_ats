package ats.vacancy.api;

import ats.api.ReferenceApi;
import ats.gateway.model.CreateSpecializationRequest;
import ats.gateway.model.ReferenceItem;
import ats.vacancy.application.SpecializationService;
import ats.vacancy.application.VacancyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReferenceController implements ReferenceApi {

    private final VacancyService vacancyService;
    private final SpecializationService specializationService;
    private final ApiMapper mapper;

    @Override
    public ResponseEntity<ReferenceItem> createSpecialization(
            CreateSpecializationRequest createSpecializationRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        mapper.toReferenceItem(
                                specializationService.create(
                                        createSpecializationRequest.getName(),
                                        createSpecializationRequest.getCode())));
    }

    @Override
    public ResponseEntity<List<ReferenceItem>> listLevels() {
        return ResponseEntity.ok(
                vacancyService.levels().stream().map(mapper::toReferenceItem).toList());
    }

    @Override
    public ResponseEntity<List<ReferenceItem>> listSpecializations() {
        return ResponseEntity.ok(
                vacancyService.specializations().stream().map(mapper::toReferenceItem).toList());
    }
}
