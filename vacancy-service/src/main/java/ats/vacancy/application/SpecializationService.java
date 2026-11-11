package ats.vacancy.application;

import ats.vacancy.infrastructure.persistence.SpecializationEntity;
import ats.vacancy.infrastructure.persistence.SpecializationRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpecializationService {

    private static final String CODE_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";
    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 128;
    private static final Map<Character, String> TRANSLITERATION =
            Map.ofEntries(
                    Map.entry('а', "a"),
                    Map.entry('б', "b"),
                    Map.entry('в', "v"),
                    Map.entry('г', "g"),
                    Map.entry('д', "d"),
                    Map.entry('е', "e"),
                    Map.entry('ё', "e"),
                    Map.entry('ж', "zh"),
                    Map.entry('з', "z"),
                    Map.entry('и', "i"),
                    Map.entry('й', "y"),
                    Map.entry('к', "k"),
                    Map.entry('л', "l"),
                    Map.entry('м', "m"),
                    Map.entry('н', "n"),
                    Map.entry('о', "o"),
                    Map.entry('п', "p"),
                    Map.entry('р', "r"),
                    Map.entry('с', "s"),
                    Map.entry('т', "t"),
                    Map.entry('у', "u"),
                    Map.entry('ф', "f"),
                    Map.entry('х', "h"),
                    Map.entry('ц', "ts"),
                    Map.entry('ч', "ch"),
                    Map.entry('ш', "sh"),
                    Map.entry('щ', "sch"),
                    Map.entry('ъ', ""),
                    Map.entry('ы', "y"),
                    Map.entry('ь', ""),
                    Map.entry('э', "e"),
                    Map.entry('ю', "yu"),
                    Map.entry('я', "ya"));

    private final SpecializationRepository repository;

    @Transactional
    public ReferenceItem create(String rawName, String requestedCode) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) {
            throw ApiException.unprocessable("SPECIALIZATION_NAME_REQUIRED", "name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw ApiException.unprocessable(
                    "SPECIALIZATION_NAME_TOO_LONG",
                    "name is longer than " + MAX_NAME_LENGTH + " characters");
        }
        if (repository.findByNameIgnoreCase(name).isPresent()) {
            throw ApiException.conflict(
                    "SPECIALIZATION_EXISTS", "Specialization with this name already exists");
        }

        String code =
                requestedCode == null || requestedCode.isBlank()
                        ? slug(name)
                        : requestedCode.strip().toLowerCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw ApiException.unprocessable(
                    "SPECIALIZATION_CODE_REQUIRED",
                    "Cannot generate code from name, provide code explicitly");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw ApiException.unprocessable(
                    "SPECIALIZATION_CODE_TOO_LONG",
                    "code is longer than " + MAX_CODE_LENGTH + " characters");
        }
        if (!code.matches(CODE_PATTERN)) {
            throw ApiException.unprocessable(
                    "INVALID_SPECIALIZATION_CODE", "code must match " + CODE_PATTERN);
        }
        if (repository.findByCode(code).isPresent()) {
            throw ApiException.conflict(
                    "SPECIALIZATION_EXISTS", "Specialization with this code already exists");
        }

        Instant now = Instant.now();
        SpecializationEntity entity =
                SpecializationEntity.builder()
                        .code(code)
                        .name(name)
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
        repository.save(entity);
        return new ReferenceItem(code, name);
    }

    static String slug(String name) {
        StringBuilder transliterated = new StringBuilder();
        for (char character : name.toLowerCase(Locale.ROOT).toCharArray()) {
            String mapped = TRANSLITERATION.get(character);
            if (mapped != null) {
                transliterated.append(mapped);
            } else if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')) {
                transliterated.append(character);
            } else {
                transliterated.append('-');
            }
        }
        return transliterated.toString().replaceAll("-+", "-").replaceAll("^-|-$", "");
    }
}
