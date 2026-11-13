package ats.vacancy.application;

import java.util.List;

public record UpdateTemplateCommand(
        int baseVersion,
        String specialization,
        String level,
        String title,
        boolean confirm,
        List<UpdateRequirementCommand> requirements) {}
