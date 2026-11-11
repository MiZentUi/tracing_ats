package ats.vacancy.template;

import java.util.List;

public record CleanedTemplate(
        String title, List<DraftRequirement> requirements, int droppedRequirements) {}
