package ats.vacancy.application;

import ats.vacancy.config.TemplateProperties;
import ats.vacancy.infrastructure.persistence.LevelEntity;
import ats.vacancy.infrastructure.persistence.LevelRepository;
import ats.vacancy.infrastructure.persistence.NewSkillWriter;
import ats.vacancy.infrastructure.persistence.RequirementEntity;
import ats.vacancy.infrastructure.persistence.RequirementRepository;
import ats.vacancy.infrastructure.persistence.ScoringWeightsEntity;
import ats.vacancy.infrastructure.persistence.ScoringWeightsRepository;
import ats.vacancy.infrastructure.persistence.SkillKindStatWriter;
import ats.vacancy.infrastructure.persistence.SpecializationEntity;
import ats.vacancy.infrastructure.persistence.SpecializationRepository;
import ats.vacancy.infrastructure.persistence.TemplateVersionEntity;
import ats.vacancy.infrastructure.persistence.TemplateVersionRepository;
import ats.vacancy.infrastructure.persistence.VacancyEntity;
import ats.vacancy.infrastructure.persistence.VacancyRepository;
import ats.vacancy.template.DraftRequirement;
import ats.vacancy.template.ExtractedTemplate;
import ats.vacancy.template.Kind;
import ats.vacancy.template.KindSource;
import ats.vacancy.template.ResolvedSkill;
import ats.vacancy.template.SkillCatalog;
import ats.vacancy.template.SkillRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VacancyStore {

    private static final String HIDDEN_LEVEL = "none";

    private final VacancyRepository vacancyRepository;
    private final TemplateVersionRepository versionRepository;
    private final RequirementRepository requirementRepository;
    private final SpecializationRepository specializationRepository;
    private final LevelRepository levelRepository;
    private final ScoringWeightsRepository scoringWeightsRepository;
    private final SkillCatalog skillCatalog;
    private final NewSkillWriter newSkillWriter;
    private final SkillKindStatWriter statWriter;
    private final VacancyViewAssembler assembler;
    private final TemplateProperties templateProperties;

    @Transactional
    public VacancyView create(
            String text,
            String sourceType,
            String sourceRef,
            String title,
            SpecializationEntity specialization,
            LevelEntity level,
            ExtractedTemplate extracted) {
        ScoringWeightsEntity weights = activeWeights();
        Instant now = Instant.now();

        VacancyEntity vacancy =
                VacancyEntity.builder()
                        .title(trimTitle(title))
                        .status("DRAFT")
                        .sourceType(sourceType)
                        .sourceRef(sourceRef)
                        .sourceText(text)
                        .currentVersion(1)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
        vacancyRepository.save(vacancy);

        TemplateVersionEntity version =
                TemplateVersionEntity.builder()
                        .vacancyId(vacancy.getId())
                        .versionNo(1)
                        .specializationId(specialization.getId())
                        .levelId(level.getId())
                        .githubWeight(level.getGithubWeight())
                        .scoringWeightsId(weights.getId())
                        .llmPromptVersion(extracted.promptVersion())
                        .llmModel(extracted.model())
                        .droppedRequirements(extracted.droppedRequirements())
                        .confirmed(false)
                        .createdAt(now)
                        .build();
        versionRepository.save(version);

        saveRequirements(version, extracted.requirements());
        return assembler.assemble(vacancy, version);
    }

    @Transactional
    public VacancyView update(UUID vacancyId, UpdateTemplateCommand command) {
        VacancyEntity vacancy =
                vacancyRepository
                        .findByIdForUpdate(vacancyId)
                        .orElseThrow(() -> ApiException.notFound("Vacancy not found"));

        if (vacancy.getCurrentVersion() != command.baseVersion()) {
            throw ApiException.conflict(
                    "VERSION_CONFLICT",
                    "Template was changed elsewhere: current version is "
                            + vacancy.getCurrentVersion());
        }
        TemplateVersionEntity previous =
                versionRepository
                        .findFirstByVacancyIdOrderByVersionNoDesc(vacancyId)
                        .orElseThrow(() -> ApiException.notFound("Vacancy template not found"));
        TemplateVersionEntity oldConfirmed =
                versionRepository
                        .findFirstByVacancyIdAndConfirmedTrueOrderByVersionNoDesc(vacancyId)
                        .orElse(null);

        SpecializationEntity specialization =
                resolveSpecialization(
                        command.specialization() == null ? null : command.specialization(),
                        previous.getSpecializationId());
        LevelEntity level =
                resolveLevel(command.level(), previous.getLevelId(), specialization.isIt());
        ScoringWeightsEntity weights = activeWeights();

        if (command.requirements() == null || command.requirements().isEmpty()) {
            throw ApiException.unprocessable(
                    "EMPTY_TEMPLATE", "Template must contain at least one requirement");
        }

        Map<UUID, RequirementEntity> previousById = new HashMap<>();
        for (RequirementEntity requirement :
                requirementRepository.findByTemplateVersionIdOrderByPosition(previous.getId())) {
            previousById.put(requirement.getId(), requirement);
        }

        Map<String, SkillRef> aliases = skillCatalog.loadAliases();
        List<PreparedRequirement> prepared =
                prepareRequirements(command.requirements(), previousById, aliases);
        rejectDuplicates(prepared);

        Instant now = Instant.now();
        if (command.title() != null && !command.title().isBlank()) {
            vacancy.setTitle(trimTitle(command.title()));
        }
        if (command.sourceText() != null) {
            String sourceText = command.sourceText().strip();
            if (sourceText.isEmpty()) {
                throw ApiException.unprocessable(
                        "TEXT_REQUIRED", "Vacancy source text must not be empty");
            }
            if (sourceText.length() > templateProperties.getMaxTextChars()) {
                throw ApiException.unprocessable(
                        "TEXT_TOO_LONG",
                        "Text is longer than "
                                + templateProperties.getMaxTextChars()
                                + " characters");
            }
            vacancy.setSourceText(sourceText);
            vacancy.setSourceType("TEXT");
        }
        vacancy.setStatus(command.confirm() ? "ACTIVE" : "DRAFT");
        vacancy.setUpdatedAt(now);

        TemplateVersionEntity version =
                TemplateVersionEntity.builder()
                        .vacancyId(vacancyId)
                        .versionNo(previous.getVersionNo() + 1)
                        .specializationId(specialization.getId())
                        .levelId(level.getId())
                        .githubWeight(level.getGithubWeight())
                        .scoringWeightsId(weights.getId())
                        .llmPromptVersion(previous.getLlmPromptVersion())
                        .llmModel(previous.getLlmModel())
                        .droppedRequirements(previous.getDroppedRequirements())
                        .confirmed(command.confirm())
                        .createdAt(now)
                        .build();
        versionRepository.save(version);

        for (int index = 0; index < prepared.size(); index++) {
            PreparedRequirement item = prepared.get(index);
            RequirementEntity entity =
                    RequirementEntity.builder()
                            .templateVersionId(version.getId())
                            .position((short) (index + 1))
                            .skillId(item.resolved().skillId())
                            .skillText(item.displaySkill())
                            .rawText(item.quote())
                            .kind(item.kind().name())
                            .kindSource(item.kindSource().name())
                            .llmKind(item.llmKind() == null ? null : item.llmKind().name())
                            .critical(false)
                            .yearsRequired(item.yearsRequired())
                            .clarityWarning(item.clarityWarning())
                            .build();
            requirementRepository.save(entity);
        }

        vacancy.setCurrentVersion(version.getVersionNo());
        vacancyRepository.save(vacancy);

        if (command.confirm()) {
            updateStats(oldConfirmed, version, prepared, specialization.getId(), level.getId());
        }
        return assembler.assemble(vacancy, version);
    }

    private List<PreparedRequirement> prepareRequirements(
            List<UpdateRequirementCommand> commands,
            Map<UUID, RequirementEntity> previousById,
            Map<String, SkillRef> aliases) {
        List<PreparedRequirement> prepared = new ArrayList<>(commands.size());
        for (UpdateRequirementCommand command : commands) {
            if (command == null || command.kind() == null) {
                throw ApiException.unprocessable("INVALID_REQUIREMENT", "kind is required");
            }
            String rawSkill = command.skill() == null ? null : command.skill().strip();
            if (rawSkill != null && rawSkill.length() > 128) {
                throw ApiException.unprocessable(
                        "SKILL_TOO_LONG", "Skill name is longer than 128 characters");
            }
            if (rawSkill != null && rawSkill.isEmpty()) {
                rawSkill = null;
            }
            String quote =
                    command.quote() == null || command.quote().isBlank()
                            ? null
                            : command.quote().strip();
            String clarity =
                    command.clarityWarning() == null || command.clarityWarning().isBlank()
                            ? null
                            : command.clarityWarning().strip();
            if (rawSkill == null && quote == null) {
                // Ограничение БД требует skill_id, skill_text или raw_text.
                throw ApiException.unprocessable(
                        "INVALID_REQUIREMENT", "Requirement must have a skill or quote");
            }

            ResolvedSkill resolved = skillCatalog.resolve(rawSkill, aliases);
            if (resolved.skillId() != null) {
                resolved = newSkillWriter.ensureMirror(resolved);
                aliases.put(
                        resolved.normalizedKey(),
                        new SkillRef(resolved.skillId(), resolved.name()));
            } else if (resolved.status() == ats.vacancy.template.NormalizationStatus.NEW
                    && resolved.name() != null
                    && resolved.normalizedKey() != null
                    && !resolved.normalizedKey().isBlank()) {
                resolved = newSkillWriter.ensureSkill(resolved);
                aliases.put(
                        resolved.normalizedKey(),
                        new SkillRef(resolved.skillId(), resolved.name()));
            }

            BigDecimal years = command.yearsRequired();
            if (years == null) {
                years = BigDecimal.ZERO;
            }
            if (years.compareTo(BigDecimal.ZERO) < 0
                    || years.compareTo(BigDecimal.valueOf(50)) > 0) {
                throw ApiException.unprocessable(
                        "INVALID_YEARS_REQUIRED", "years_required must be between 0 and 50");
            }

            RequirementEntity previous =
                    command.id() == null ? null : previousById.get(parseId(command.id()));
            KindSource kindSource;
            Kind llmKind;
            if (previous == null) {
                kindSource = KindSource.HR;
                llmKind = null;
            } else {
                llmKind =
                        previous.getLlmKind() == null ? null : Kind.valueOf(previous.getLlmKind());
                kindSource =
                        previous.getKind().equals(command.kind().name())
                                ? KindSource.valueOf(previous.getKindSource())
                                : KindSource.HR;
            }

            String displaySkill = resolved.skillId() == null ? rawSkill : resolved.name();
            prepared.add(
                    new PreparedRequirement(
                            resolved,
                            displaySkill,
                            command.kind(),
                            kindSource,
                            llmKind,
                            years,
                            quote,
                            clarity));
        }
        return prepared;
    }

    private static UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable("INVALID_REQUIREMENT_ID", "Invalid requirement id");
        }
    }

    private static void rejectDuplicates(List<PreparedRequirement> prepared) {
        Set<String> seen = new HashSet<>();
        List<Integer> duplicates = new ArrayList<>();
        for (int index = 0; index < prepared.size(); index++) {
            PreparedRequirement item = prepared.get(index);
            String key;
            if (item.resolved().skillId() != null) {
                key = "S:" + item.resolved().skillId();
            } else if (item.resolved().normalizedKey() != null
                    && !item.resolved().normalizedKey().isBlank()) {
                key = "N:" + item.resolved().normalizedKey();
            } else {
                continue;
            }
            if (!seen.add(key)) {
                duplicates.add(index);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DUPLICATE_SKILL",
                    "Duplicate skills at indexes " + duplicates);
        }
    }

    @Transactional
    public void delete(UUID vacancyId) {
        VacancyEntity vacancy =
                vacancyRepository
                        .findByIdForUpdate(vacancyId)
                        .orElseThrow(() -> ApiException.notFound("Vacancy not found"));

        versionRepository
                .findFirstByVacancyIdAndConfirmedTrueOrderByVersionNoDesc(vacancyId)
                .ifPresent(
                        version ->
                                contributionOf(version)
                                        .forEach(
                                                (key, count) ->
                                                        statWriter.applyDelta(
                                                                key.specializationId(),
                                                                key.levelId(),
                                                                key.skillId(),
                                                                key.kind(),
                                                                -count)));

        vacancyRepository.delete(vacancy);
    }

    private Map<StatKey, Integer> contributionOf(TemplateVersionEntity version) {
        Map<StatKey, Integer> contribution = new LinkedHashMap<>();
        for (RequirementEntity requirement :
                requirementRepository.findByTemplateVersionIdOrderByPosition(version.getId())) {
            if (requirement.getSkillId() != null) {
                contribution.put(
                        new StatKey(
                                version.getSpecializationId(),
                                version.getLevelId(),
                                requirement.getSkillId(),
                                requirement.getKind()),
                        1);
            }
        }
        return contribution;
    }

    private void updateStats(
            TemplateVersionEntity oldConfirmed,
            TemplateVersionEntity newVersion,
            List<PreparedRequirement> prepared,
            UUID specializationId,
            UUID levelId) {
        Map<StatKey, Integer> oldContribution =
                oldConfirmed == null ? Map.of() : contributionOf(oldConfirmed);

        Map<StatKey, Integer> newContribution = new LinkedHashMap<>();
        for (PreparedRequirement item : prepared) {
            if (item.resolved().skillId() != null) {
                newContribution.put(
                        new StatKey(
                                specializationId,
                                levelId,
                                item.resolved().skillId(),
                                item.kind().name()),
                        1);
            }
        }

        Set<StatKey> allKeys = new HashSet<>(oldContribution.keySet());
        allKeys.addAll(newContribution.keySet());
        for (StatKey key : allKeys) {
            int delta = newContribution.getOrDefault(key, 0) - oldContribution.getOrDefault(key, 0);
            if (delta != 0) {
                statWriter.applyDelta(
                        key.specializationId(), key.levelId(), key.skillId(), key.kind(), delta);
            }
        }
    }

    private void saveRequirements(
            TemplateVersionEntity version, List<DraftRequirement> requirements) {
        for (int index = 0; index < requirements.size(); index++) {
            DraftRequirement draft = requirements.get(index);
            UUID skillId = draft.skillId();
            String skillText = draft.skill();
            if (skillId != null) {
                ResolvedSkill mirrored =
                        newSkillWriter.ensureMirror(
                                new ResolvedSkill(
                                        draft.normalizedKey(),
                                        ats.vacancy.template.NormalizationStatus.MATCHED,
                                        skillId,
                                        draft.skill(),
                                        draft.quote()));
                skillId = mirrored.skillId();
                skillText = mirrored.name();
            }
            RequirementEntity entity =
                    RequirementEntity.builder()
                            .templateVersionId(version.getId())
                            .position((short) (index + 1))
                            .skillId(skillId)
                            .skillText(skillText)
                            .rawText(draft.quote())
                            .kind(draft.kind().name())
                            .kindSource(draft.kindSource().name())
                            .llmKind(draft.llmKind() == null ? null : draft.llmKind().name())
                            .critical(false)
                            .yearsRequired(
                                    draft.yearsRequired() == null
                                            ? BigDecimal.ZERO
                                            : draft.yearsRequired())
                            .clarityWarning(draft.clarityWarning())
                            .build();
            requirementRepository.save(entity);
        }
    }

    private ScoringWeightsEntity activeWeights() {
        return scoringWeightsRepository
                .findFirstByStatus("ACTIVE")
                .orElseThrow(
                        () ->
                                ApiException.conflict(
                                        "NO_ACTIVE_SCORING_WEIGHTS",
                                        "No active scoring weights profile"));
    }

    private SpecializationEntity resolveSpecialization(String code, UUID fallbackId) {
        if (code == null || code.isBlank()) {
            return specializationRepository
                    .findById(fallbackId)
                    .orElseThrow(() -> ApiException.notFound("Specialization not found"));
        }
        return specializationRepository
                .findByCodeAndActiveTrue(code)
                .orElseThrow(
                        () ->
                                ApiException.unprocessable(
                                        "UNKNOWN_SPECIALIZATION",
                                        "Unknown specialization: " + code));
    }

    private LevelEntity resolveLevel(String code, UUID fallbackId, boolean it) {
        if (!it) {
            return levelRepository
                    .findByCode(HIDDEN_LEVEL)
                    .orElseThrow(
                            () -> new IllegalStateException("Hidden level 'none' is not seeded"));
        }
        if (code == null || code.isBlank()) {
            return levelRepository
                    .findById(fallbackId)
                    .orElseThrow(() -> ApiException.notFound("Level not found"));
        }
        if (HIDDEN_LEVEL.equals(code)) {
            throw ApiException.unprocessable(
                    "LEVEL_REQUIRED", "level is required for IT specialization");
        }
        return levelRepository
                .findByCodeAndActiveTrue(code)
                .orElseThrow(
                        () ->
                                ApiException.unprocessable(
                                        "UNKNOWN_LEVEL", "Unknown level: " + code));
    }

    private static String trimTitle(String title) {
        String value = title == null ? "" : title.strip();
        if (value.isEmpty()) {
            value = "Вакансия";
        }
        return value.length() > 256 ? value.substring(0, 256) : value;
    }

    private record StatKey(UUID specializationId, UUID levelId, UUID skillId, String kind) {}

    private record PreparedRequirement(
            ResolvedSkill resolved,
            String displaySkill,
            Kind kind,
            KindSource kindSource,
            Kind llmKind,
            BigDecimal yearsRequired,
            String quote,
            String clarityWarning) {}
}
