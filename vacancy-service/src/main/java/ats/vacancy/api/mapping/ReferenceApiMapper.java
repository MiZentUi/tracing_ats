package ats.vacancy.api.mapping;

import ats.gateway.model.ReferenceItem;
import ats.gateway.model.Specialization;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/** Справочники (специализации, уровни) в DTO OpenAPI. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReferenceApiMapper {

    ReferenceItem toReferenceItem(ats.vacancy.application.ReferenceItem item);

    Specialization toSpecialization(ats.vacancy.application.SpecializationItem item);
}
