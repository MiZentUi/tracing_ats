package ats.vacancy.api;

import ats.gateway.model.KindSource;
import ats.gateway.model.NormalizationStatus;
import ats.gateway.model.ReferenceItem;
import ats.gateway.model.Requirement;
import ats.gateway.model.SkillKind;
import ats.gateway.model.SkillKindHistory;
import ats.gateway.model.TemplateMeta;
import ats.gateway.model.UpdateRequirement;
import ats.gateway.model.UpdateTemplateRequest;
import ats.gateway.model.Vacancy;
import ats.gateway.model.VacancyStatus;
import ats.gateway.model.VacancySummary;
import ats.gateway.model.VacancyTemplate;
import ats.vacancy.application.RequirementView;
import ats.vacancy.application.UpdateRequirementCommand;
import ats.vacancy.application.UpdateTemplateCommand;
import ats.vacancy.application.VacancyPage;
import ats.vacancy.application.VacancySummaryView;
import ats.vacancy.application.VacancyView;
import ats.vacancy.template.Kind;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApiMapper {

    public Vacancy toVacancy(VacancyView view) {
        List<Requirement> requirements =
                view.requirements().stream().map(this::toRequirement).toList();

        VacancyTemplate template =
                new VacancyTemplate()
                        .version(view.version())
                        .isConfirmed(view.confirmed())
                        .requirements(requirements)
                        .meta(
                                new TemplateMeta()
                                        .model(view.model())
                                        .promptVersion(view.promptVersion())
                                        .droppedRequirements(view.droppedRequirements()));

        return new Vacancy()
                .vacancyId(view.id())
                .specialization(view.specialization())
                .level(view.level())
                .title(view.title())
                .status(VacancyStatus.fromValue(view.status()))
                .template(template);
    }

    public ats.gateway.model.VacancyList toVacancyList(VacancyPage page) {
        return new ats.gateway.model.VacancyList()
                .items(page.items().stream().map(this::toSummary).toList())
                .total(page.total());
    }

    private VacancySummary toSummary(VacancySummaryView view) {
        return new VacancySummary()
                .id(view.id())
                .title(view.title())
                .specialization(view.specialization())
                .level(view.level())
                .status(VacancyStatus.fromValue(view.status()))
                .version(view.version())
                .isConfirmed(view.confirmed())
                .requirementsCount(view.requirementsCount())
                .createdAt(view.createdAt().atOffset(java.time.ZoneOffset.UTC));
    }

    private Requirement toRequirement(RequirementView view) {
        return new Requirement()
                .id(view.id().toString())
                .skill(view.skill())
                .skillId(view.skillId())
                .normalizationStatus(
                        NormalizationStatus.fromValue(view.normalizationStatus().name()))
                .kind(SkillKind.fromValue(view.kind().name()))
                .kindSource(KindSource.fromValue(view.kindSource().name()))
                .llmKind(view.llmKind() == null ? null : SkillKind.fromValue(view.llmKind().name()))
                .yearsRequired(
                        view.yearsRequired() == null ? null : view.yearsRequired().doubleValue())
                .quote(view.quote())
                .clarityWarning(view.clarityWarning())
                .history(toHistory(view));
    }

    private SkillKindHistory toHistory(RequirementView view) {
        if (view.history() == null) {
            return null;
        }
        return new SkillKindHistory()
                .must(view.history().must())
                .nice(view.history().nice())
                .optional(view.history().optional());
    }

    public ReferenceItem toReferenceItem(ats.vacancy.application.ReferenceItem item) {
        return new ReferenceItem().code(item.code()).name(item.name());
    }

    public UpdateTemplateCommand toCommand(UpdateTemplateRequest request) {
        List<UpdateRequirementCommand> requirements =
                request.getRequirements() == null
                        ? List.of()
                        : request.getRequirements().stream().map(this::toCommand).toList();
        return new UpdateTemplateCommand(
                request.getBaseVersion() == null ? -1 : request.getBaseVersion(),
                request.getSpecialization(),
                request.getLevel(),
                request.getTitle(),
                requirements);
    }

    private UpdateRequirementCommand toCommand(UpdateRequirement requirement) {
        BigDecimal years =
                requirement.getYearsRequired() == null
                        ? null
                        : BigDecimal.valueOf(requirement.getYearsRequired());
        return new UpdateRequirementCommand(
                requirement.getId(),
                requirement.getSkill(),
                requirement.getKind() == null
                        ? null
                        : Kind.valueOf(requirement.getKind().getValue()),
                years,
                requirement.getQuote(),
                requirement.getClarityWarning());
    }
}
