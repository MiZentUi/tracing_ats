package ats.vacancy.application;

import ats.vacancy.infrastructure.persistence.LevelEntity;
import ats.vacancy.infrastructure.persistence.LevelRepository;
import ats.vacancy.infrastructure.persistence.RequirementEntity;
import ats.vacancy.infrastructure.persistence.RequirementRepository;
import ats.vacancy.infrastructure.persistence.SpecializationEntity;
import ats.vacancy.infrastructure.persistence.SpecializationRepository;
import ats.vacancy.infrastructure.persistence.TemplateVersionEntity;
import ats.vacancy.infrastructure.persistence.TemplateVersionRepository;
import ats.vacancy.infrastructure.persistence.VacancyEntity;
import ats.vacancy.infrastructure.persistence.VacancyRepository;
import ats.vacancy.template.Kind;
import ats.vacancy.template.KindHistoryService;
import ats.vacancy.template.KindSource;
import ats.vacancy.template.NormalizationStatus;
import ats.vacancy.template.SkillKindHistory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VacancyViewAssembler {

    private final VacancyRepository vacancyRepository;
    private final TemplateVersionRepository versionRepository;
    private final RequirementRepository requirementRepository;
    private final SpecializationRepository specializationRepository;
    private final LevelRepository levelRepository;
    private final KindHistoryService historyService;

    @Transactional(readOnly = true)
    public VacancyView assemble(VacancyEntity vacancy) {
        TemplateVersionEntity version =
                versionRepository
                        .findByVacancyIdAndVersionNo(vacancy.getId(), vacancy.getCurrentVersion())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Current template version is missing for vacancy "
                                                        + vacancy.getId()));
        return assemble(vacancy, version);
    }

    @Transactional(readOnly = true)
    public VacancyView assemble(VacancyEntity vacancy, TemplateVersionEntity version) {
        List<RequirementEntity> requirements =
                requirementRepository.findByTemplateVersionIdOrderByPosition(version.getId());
        List<UUID> skillIds =
                requirements.stream()
                        .map(RequirementEntity::getSkillId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
        Map<UUID, SkillKindHistory> stats =
                historyService.loadStatsForSkills(
                        version.getSpecializationId(), version.getLevelId(), skillIds);

        String specializationCode =
                specializationRepository
                        .findById(version.getSpecializationId())
                        .map(SpecializationEntity::getCode)
                        .orElse("");
        String levelCode =
                levelRepository.findById(version.getLevelId()).map(LevelEntity::getCode).orElse("");

        List<RequirementView> mapped = new ArrayList<>(requirements.size());
        for (RequirementEntity requirement : requirements) {
            SkillKindHistory history =
                    requirement.getSkillId() == null ? null : stats.get(requirement.getSkillId());
            if (history != null && history.isEmpty()) {
                history = null;
            }
            mapped.add(
                    new RequirementView(
                            requirement.getId(),
                            requirement.getSkillText(),
                            requirement.getSkillId(),
                            requirement.getSkillId() == null
                                    ? NormalizationStatus.NEW
                                    : NormalizationStatus.MATCHED,
                            Kind.valueOf(requirement.getKind()),
                            KindSource.valueOf(requirement.getKindSource()),
                            requirement.getLlmKind() == null
                                    ? null
                                    : Kind.valueOf(requirement.getLlmKind()),
                            normalizeYears(requirement.getYearsRequired()),
                            requirement.getRawText(),
                            requirement.getClarityWarning(),
                            history));
        }

        return new VacancyView(
                vacancy.getId(),
                specializationCode,
                levelCode,
                vacancy.getTitle(),
                vacancy.getStatus(),
                vacancy.getSourceText(),
                version.getVersionNo(),
                version.isConfirmed(),
                mapped,
                version.getLlmModel(),
                version.getLlmPromptVersion(),
                version.getDroppedRequirements());
    }

    @Transactional(readOnly = true)
    public VacancyPage list(int limit, int offset) {
        List<VacancyEntity> vacancies = vacancyRepository.findPage(limit, offset);
        long total = vacancyRepository.count();
        List<UUID> ids = vacancies.stream().map(VacancyEntity::getId).toList();
        Map<UUID, TemplateVersionEntity> currentVersions = new HashMap<>();
        if (!ids.isEmpty()) {
            for (TemplateVersionEntity version : versionRepository.findByVacancyIdIn(ids)) {
                currentVersions.merge(
                        version.getVacancyId(),
                        version,
                        (left, right) ->
                                left.getVersionNo() >= right.getVersionNo() ? left : right);
            }
        }
        Map<UUID, String> specializations =
                specializationRepository.findAll().stream()
                        .collect(
                                Collectors.toMap(
                                        SpecializationEntity::getId,
                                        SpecializationEntity::getCode,
                                        (left, right) -> left));
        Map<UUID, String> levels =
                levelRepository.findAll().stream()
                        .collect(
                                Collectors.toMap(
                                        LevelEntity::getId,
                                        LevelEntity::getCode,
                                        (left, right) -> left));

        Map<UUID, Integer> requirementCounts = new HashMap<>();
        List<UUID> versionIds =
                currentVersions.values().stream().map(TemplateVersionEntity::getId).toList();
        if (!versionIds.isEmpty()) {
            requirementRepository
                    .findByTemplateVersionIdIn(versionIds)
                    .forEach(
                            requirement ->
                                    requirementCounts.merge(
                                            requirement.getTemplateVersionId(), 1, Integer::sum));
        }

        List<VacancySummaryView> items = new ArrayList<>(vacancies.size());
        for (VacancyEntity vacancy : vacancies) {
            TemplateVersionEntity version = currentVersions.get(vacancy.getId());
            if (version == null) {
                continue;
            }
            items.add(
                    new VacancySummaryView(
                            vacancy.getId(),
                            vacancy.getTitle(),
                            specializations.getOrDefault(version.getSpecializationId(), ""),
                            levels.getOrDefault(version.getLevelId(), ""),
                            vacancy.getStatus(),
                            version.getVersionNo(),
                            version.isConfirmed(),
                            requirementCounts.getOrDefault(version.getId(), 0),
                            vacancy.getCreatedAt()));
        }
        return new VacancyPage(items, total);
    }

    @Transactional(readOnly = true)
    public List<ReferenceItem> listSpecializations() {
        return specializationRepository.findAllByActiveTrueOrderByName().stream()
                .map(item -> new ReferenceItem(item.getCode(), item.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReferenceItem> listLevels() {
        return levelRepository.findAllByActiveTrueOrderBySortOrder().stream()
                .map(item -> new ReferenceItem(item.getCode(), item.getName()))
                .toList();
    }

    private static BigDecimal normalizeYears(BigDecimal years) {
        if (years == null || years.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return years;
    }
}
