package ats.vacancy.dictionary.generation;

import ats.vacancy.dictionary.generation.SkillGenerationModels.ApproveRequest;
import ats.vacancy.dictionary.generation.SkillGenerationModels.GenerationJob;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BFF админской генерации навыков по специальности. */
@RestController
@RequestMapping("/api/v1/admin/dictionary/generation-jobs")
@RequiredArgsConstructor
public class SkillGenerationController {

    private final SkillGenerationService service;

    @PostMapping
    public GenerationJob generate(@RequestBody CreateRequest request) {
        return service.generate(request.specialization(), request.note(), request.maxSkills());
    }

    @GetMapping
    public List<GenerationJob> list(@RequestParam(defaultValue = "20") int limit) {
        return service.list(limit);
    }

    @GetMapping("/{id}")
    public GenerationJob get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    public GenerationJob approve(
            @PathVariable UUID id, @RequestBody(required = false) ApproveRequest request) {
        return service.approve(id, request == null ? null : request.skills());
    }

    @PostMapping("/{id}/reject")
    public GenerationJob reject(@PathVariable UUID id) {
        return service.reject(id);
    }

    public record CreateRequest(String specialization, String note, Integer maxSkills) {}
}
