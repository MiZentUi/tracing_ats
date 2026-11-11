package ats.vacancy.template;

public record HistoryDecision(
        Kind kind, KindSource source, Kind llmKind, SkillKindHistory history) {}
