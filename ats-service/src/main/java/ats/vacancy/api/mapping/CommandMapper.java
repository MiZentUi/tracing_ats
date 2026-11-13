package ats.vacancy.api.mapping;

import ats.gateway.model.UpdateTemplateRequest;
import ats.vacancy.application.UpdateRequirementCommand;
import ats.vacancy.application.UpdateTemplateCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Запрос на сохранение шаблона в доменную команду.
 *
 * <p>Написано руками, а не MapStruct: отсутствующий {@code base_version} превращается в -1 (явное
 * нарушение версии), а DTO-мапперы остаются отдельно от записи команд.
 */
@Component
@RequiredArgsConstructor
public class CommandMapper {

    private final RequirementApiMapper requirementApiMapper;

    public UpdateTemplateCommand toCommand(UpdateTemplateRequest request) {
        List<UpdateRequirementCommand> requirements =
                request.getRequirements() == null
                        ? List.of()
                        : request.getRequirements().stream()
                                .map(requirementApiMapper::toCommand)
                                .toList();
        return new UpdateTemplateCommand(
                request.getBaseVersion() == null ? -1 : request.getBaseVersion(),
                request.getSpecialization(),
                request.getLevel(),
                request.getTitle(),
                request.getConfirm() == null || request.getConfirm(),
                requirements);
    }
}
