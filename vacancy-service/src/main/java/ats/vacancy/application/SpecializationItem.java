package ats.vacancy.application;

/**
 * Специализация в справочнике. {@code it} определяет, обязателен ли уровень (грейд): для
 * IT-специализаций — да, для остальных — нет.
 */
public record SpecializationItem(String code, String name, boolean it) {}
