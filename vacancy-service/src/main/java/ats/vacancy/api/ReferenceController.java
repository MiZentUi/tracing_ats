package ats.vacancy.api;

import ats.api.ReferenceApi;
import ats.gateway.model.ReferenceItem;
import ats.vacancy.application.VacancyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReferenceController implements ReferenceApi {

    private final VacancyService vacancyService;
    private final ApiMapper mapper;

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
