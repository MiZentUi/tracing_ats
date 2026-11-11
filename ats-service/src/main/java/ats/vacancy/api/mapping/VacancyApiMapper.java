package ats.vacancy.api.mapping;

import ats.gateway.model.Vacancy;
import ats.gateway.model.VacancyList;
import ats.gateway.model.VacancySummary;
import ats.vacancy.application.VacancyPage;
import ats.vacancy.application.VacancySummaryView;
import ats.vacancy.application.VacancyView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** Вакансия и её список в DTO OpenAPI. Требования делегируются в {@link RequirementApiMapper}. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = RequirementApiMapper.class,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VacancyApiMapper {

    @Mapping(target = "vacancyId", source = "id")
    @Mapping(target = "template.version", source = "version")
    @Mapping(target = "template.isConfirmed", source = "confirmed")
    @Mapping(target = "template.requirements", source = "requirements")
    @Mapping(target = "template.meta.model", source = "model")
    @Mapping(target = "template.meta.promptVersion", source = "promptVersion")
    @Mapping(target = "template.meta.droppedRequirements", source = "droppedRequirements")
    Vacancy toVacancy(VacancyView view);

    @Mapping(target = "items", source = "items")
    @Mapping(target = "total", source = "total")
    VacancyList toVacancyList(VacancyPage page);

    @Mapping(target = "isConfirmed", source = "confirmed")
    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "instantToOffset")
    VacancySummary toSummary(VacancySummaryView view);

    @Named("instantToOffset")
    default OffsetDateTime instantToOffset(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
