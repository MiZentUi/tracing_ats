package ats.resume.domain.scoring;

import java.util.List;

/**
 * Словарь открытых квалификаций. Словарь не знает, какие квалификации бывают: он только
 * сопоставляет нормализованный алиас с каноническим ключом. Реальная реализация появится позже,
 * пока используется {@code StubQualificationCatalog}.
 */
public interface QualificationCatalog {

    /** Версия снимка словаря. Пишется в метаданные запуска скоринга. */
    String version();

    /** Снимок алиасов для передачи в scoring-service. */
    List<Alias> aliases();

    record Alias(String aliasNormalized, String canonicalKey, String canonicalLabel) {}
}
