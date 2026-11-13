package ats.vacancy.dictionary;

import ats.vacancy.application.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Админский BFF словаря. Живёт в ats, потому что только ats знает ссылки на skill_id в
 * vacancy_requirement и skill_kind_stat.
 */
@RestController
@RequestMapping("/api/v1/admin/dictionary")
@RequiredArgsConstructor
public class DictionaryAdminController {

    private final DictionaryClient dictionaryClient;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/version")
    public Version version() {
        return new Version(dictionaryClient.resolverVersion());
    }

    @GetMapping("/review-items")
    public List<DictionaryModels.Review> reviewItems(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return dictionaryClient.listReview(status, limit, offset);
    }

    @PostMapping("/review-items/{id}/resolve")
    public DictionaryModels.ReviewResult resolveReview(
            @PathVariable UUID id, @RequestBody ResolveReviewRequest request) {
        return dictionaryClient.resolveReview(
                id,
                request.action(),
                request.skillId(),
                request.canonicalName(),
                request.skillType(),
                request.additionalAliases());
    }

    @GetMapping("/skills")
    public List<DictionaryModels.Skill> skills(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "") String skillType,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return dictionaryClient.listSkills(query, skillType, status, limit, offset);
    }

    @PostMapping("/skills")
    public DictionaryModels.Skill createSkill(@RequestBody CreateSkillRequest request) {
        return dictionaryClient.createSkill(request.canonicalName(), request.skillType());
    }

    @PostMapping("/skills/{id}/aliases")
    public DictionaryModels.Alias addAlias(
            @PathVariable UUID id, @RequestBody AddAliasRequest request) {
        return dictionaryClient.addAlias(id, request.raw(), request.lang(), request.lemmaMode());
    }

    @PostMapping("/skills/{id}/canonical")
    public DictionaryModels.Skill setCanonical(
            @PathVariable UUID id, @RequestBody SetCanonicalRequest request) {
        return dictionaryClient.setCanonical(id, request.aliasId());
    }

    @DeleteMapping("/aliases/{id}")
    public void deleteAlias(@PathVariable UUID id) {
        dictionaryClient.deleteAlias(id);
    }

    @GetMapping("/qualifications")
    public List<DictionaryModels.Qualification> qualifications(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return dictionaryClient.listQualifications(query, status, limit, offset);
    }

    @PostMapping("/qualifications")
    public DictionaryModels.Qualification createQualification(
            @RequestBody CreateQualificationRequest request) {
        return dictionaryClient.createQualification(
                request.canonicalName(), request.qualificationType());
    }

    @PostMapping("/qualifications/{id}/aliases")
    public DictionaryModels.QualificationAlias addQualificationAlias(
            @PathVariable UUID id, @RequestBody AddQualificationAliasRequest request) {
        return dictionaryClient.addQualificationAlias(
                id, request.raw(), request.lang(), request.lemmaMode());
    }

    @PostMapping("/qualifications/{id}/canonical")
    public DictionaryModels.Qualification setCanonicalQualificationAlias(
            @PathVariable UUID id, @RequestBody SetCanonicalRequest request) {
        return dictionaryClient.setCanonicalQualificationAlias(id, request.aliasId());
    }

    @DeleteMapping("/qualification-aliases/{id}")
    public void deleteQualificationAlias(@PathVariable UUID id) {
        dictionaryClient.deleteQualificationAlias(id);
    }

    @GetMapping("/relations")
    public List<DictionaryModels.Relation> relations(
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return dictionaryClient.listRelations(limit, offset);
    }

    @PostMapping("/relations")
    public DictionaryModels.Relation createRelation(@RequestBody CreateRelationRequest request) {
        return dictionaryClient.createRelation(
                request.leftSkillId(), request.rightSkillId(), request.kind());
    }

    @DeleteMapping("/relations")
    public void deleteRelation(@RequestParam UUID left, @RequestParam UUID right) {
        dictionaryClient.deleteRelation(left, right);
    }

    @PostMapping("/skills/{id}/deprecate")
    public DictionaryModels.Skill deprecate(@PathVariable UUID id) {
        return dictionaryClient.deprecateSkill(id);
    }

    @DeleteMapping("/skills/{id}")
    public void deleteSkill(
            @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean confirmNoRefs) {
        int references = references(id);
        if (references > 0) {
            throw ApiException.conflict(
                    "SKILL_IN_USE", "Skill is referenced " + references + " times");
        }
        if (!confirmNoRefs) {
            throw ApiException.conflict(
                    "CONFIRM_REQUIRED", "Pass confirmNoRefs=true to delete the skill");
        }
        dictionaryClient.deleteSkill(id);
        jdbcTemplate.update("DELETE FROM skill WHERE id = ?::uuid", id.toString());
    }

    private int references(UUID skillId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT
                          (SELECT count(*) FROM vacancy_requirement WHERE skill_id = ?::uuid)
                        + (SELECT count(*) FROM skill_kind_stat WHERE skill_id = ?::uuid)
                        """,
                        Integer.class,
                        skillId.toString(),
                        skillId.toString());
        return count == null ? 0 : count;
    }

    public record Version(String resolverVersion) {}

    public record ResolveReviewRequest(
            String action,
            UUID skillId,
            String canonicalName,
            String skillType,
            List<String> additionalAliases) {}

    public record CreateSkillRequest(String canonicalName, String skillType) {}

    public record AddAliasRequest(String raw, String lang, String lemmaMode) {}

    public record SetCanonicalRequest(UUID aliasId) {}

    public record CreateQualificationRequest(String canonicalName, String qualificationType) {}

    public record AddQualificationAliasRequest(String raw, String lang, String lemmaMode) {}

    public record CreateRelationRequest(UUID leftSkillId, UUID rightSkillId, String kind) {}
}
