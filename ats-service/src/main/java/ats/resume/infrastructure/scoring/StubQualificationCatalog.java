package ats.resume.infrastructure.scoring;

import ats.resume.domain.scoring.QualificationCatalog;
import ats.vacancy.template.SkillNormalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Временная заглушка словаря квалификаций. Настоящий словарь появится позже и заменит этот бин
 * через {@code ats.qualification-catalog=db}. Список ниже — только демонстрационные алиасы,
 * закрытого перечня квалификаций в контракте нет.
 */
@Component
@ConditionalOnProperty(
        name = "ats.qualification-catalog",
        havingValue = "stub",
        matchIfMissing = true)
public class StubQualificationCatalog implements QualificationCatalog {

    private static final String VERSION = "stub-v1";

    private final List<Alias> aliases;

    public StubQualificationCatalog() {
        Map<String, Alias> byAlias = new LinkedHashMap<>();
        addElectricalSafetyGroup(byAlias, "1", "I");
        addElectricalSafetyGroup(byAlias, "2", "II");
        addElectricalSafetyGroup(byAlias, "3", "III");
        addElectricalSafetyGroup(byAlias, "4", "IV");
        addDriverLicense(byAlias, "b", "B");
        addDriverLicense(byAlias, "c", "C");
        add(
                byAlias,
                "аттестат профессионального бухгалтера",
                "professional_accountant",
                "аттестат профессионального бухгалтера");
        add(
                byAlias,
                "профессиональный бухгалтер",
                "professional_accountant",
                "аттестат профессионального бухгалтера");
        this.aliases = List.copyOf(byAlias.values());
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public List<Alias> aliases() {
        return aliases;
    }

    private static void addElectricalSafetyGroup(
            Map<String, Alias> byAlias, String digit, String roman) {
        String key = "electrical_safety_group_" + roman.toLowerCase(Locale.ROOT);
        String label = roman + " группа по электробезопасности";
        add(byAlias, digit + " группа по электробезопасности", key, label);
        add(byAlias, digit + "-я группа по электробезопасности", key, label);
        add(byAlias, roman.toLowerCase(Locale.ROOT) + " группа по электробезопасности", key, label);
        add(byAlias, label, key, label);
    }

    private static void addDriverLicense(Map<String, Alias> byAlias, String lower, String upper) {
        String key = "driver_license_" + lower;
        String label = "водительские права категории " + upper;
        add(byAlias, "права категории " + lower, key, label);
        add(byAlias, "водительские права категории " + lower, key, label);
        add(byAlias, label, key, label);
    }

    private static void add(
            Map<String, Alias> byAlias, String alias, String canonicalKey, String canonicalLabel) {
        String normalized = SkillNormalizer.normalizeKey(alias);
        byAlias.putIfAbsent(normalized, new Alias(normalized, canonicalKey, canonicalLabel));
    }
}
