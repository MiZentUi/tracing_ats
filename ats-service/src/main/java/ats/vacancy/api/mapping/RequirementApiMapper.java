package ats.vacancy.api.mapping;

import ats.gateway.model.Requirement;
import ats.gateway.model.SkillKind;
import ats.gateway.model.SkillKindHistory;
import ats.vacancy.application.RequirementView;
import ats.vacancy.application.UpdateRequirementCommand;
import ats.vacancy.template.Kind;
import java.math.BigDecimal;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/** Преобразование требований между доменом, командами и DTO OpenAPI. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RequirementApiMapper {

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    Requirement toRequirement(RequirementView view);

    SkillKindHistory toHistory(ats.vacancy.template.SkillKindHistory history);

    @Mapping(target = "kind", source = "kind", qualifiedByName = "toKind")
    UpdateRequirementCommand toCommand(ats.gateway.model.UpdateRequirement requirement);

    @Named("uuidToString")
    default String uuidToString(UUID value) {
        return value == null ? null : value.toString();
    }

    @Named("toKind")
    default Kind toKind(SkillKind value) {
        return value == null ? null : Kind.valueOf(value.getValue());
    }

    default BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
