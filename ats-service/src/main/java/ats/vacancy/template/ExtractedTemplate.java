package ats.vacancy.template;

import java.util.List;

public record ExtractedTemplate(
        String title,
        List<DraftRequirement> requirements,
        String model,
        String promptVersion,
        int droppedRequirements) {}
