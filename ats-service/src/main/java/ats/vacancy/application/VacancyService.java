package ats.vacancy.application;

import ats.extraction.domain.ExtractedText;
import ats.extraction.domain.TextExtractionService;
import ats.extraction.domain.UploadedFile;
import ats.vacancy.config.TemplateProperties;
import ats.vacancy.config.VacancyLlmProperties;
import ats.vacancy.infrastructure.persistence.LevelEntity;
import ats.vacancy.infrastructure.persistence.LevelRepository;
import ats.vacancy.infrastructure.persistence.SpecializationEntity;
import ats.vacancy.infrastructure.persistence.SpecializationRepository;
import ats.vacancy.infrastructure.persistence.VacancyEntity;
import ats.vacancy.infrastructure.persistence.VacancyRepository;
import ats.vacancy.llm.LlmAuthException;
import ats.vacancy.llm.LlmInvalidResponseException;
import ats.vacancy.llm.LlmNotConfiguredException;
import ats.vacancy.llm.LlmQuotaException;
import ats.vacancy.llm.LlmTruncatedException;
import ats.vacancy.llm.LlmUnavailableException;
import ats.vacancy.support.LogSupport;
import ats.vacancy.template.ExtractedTemplate;
import ats.vacancy.template.TemplateExtractionService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyService {

    private static final String HIDDEN_LEVEL = "none";

    private final VacancyRepository vacancyRepository;
    private final SpecializationRepository specializationRepository;
    private final LevelRepository levelRepository;
    private final TextExtractionService textExtractionService;
    private final TemplateExtractionService templateExtractionService;
    private final VacancyStore store;
    private final VacancyViewAssembler assembler;
    private final TemplateProperties templateProperties;
    private final VacancyLlmProperties llmProperties;

    public VacancyView create(CreateVacancyCommand command) {
        if (command.specialization() == null || command.specialization().isBlank()) {
            throw ApiException.unprocessable(
                    "SPECIALIZATION_REQUIRED", "specialization is required");
        }
        SpecializationEntity specialization =
                specializationRepository
                        .findByCodeAndActiveTrue(command.specialization())
                        .orElseThrow(
                                () ->
                                        ApiException.unprocessable(
                                                "UNKNOWN_SPECIALIZATION",
                                                "Unknown specialization: "
                                                        + command.specialization()));
        LevelEntity level = resolveLevel(specialization, command.level());

        boolean hasText = command.text() != null && !command.text().isBlank();
        boolean hasFile = command.file() != null && !command.file().isEmpty();
        if (hasText == hasFile) {
            throw ApiException.unprocessable(
                    "TEXT_OR_FILE_REQUIRED", "Provide exactly one of text or file");
        }

        String text;
        String sourceType;
        String sourceRef;
        if (hasFile) {
            if (command.file().getSize() > templateProperties.getMaxFileSize().toBytes()) {
                throw new ApiException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "FILE_TOO_LARGE",
                        "File is larger than " + templateProperties.getMaxFileSize());
            }
            String fileName =
                    command.file().getOriginalFilename() == null
                            ? "upload"
                            : command.file().getOriginalFilename();
            byte[] content;
            try {
                content = command.file().getBytes();
            } catch (IOException e) {
                throw ApiException.unprocessable("FILE_READ_ERROR", "Cannot read uploaded file");
            }
            ExtractedText extracted =
                    textExtractionService.extract(new UploadedFile(fileName, content));
            text = extracted.text();
            sourceType = "FILE";
            sourceRef = fileName;
            log.info(
                    "Vacancy source file extracted: bytes={}, type={}, pages={}, warnings={},"
                            + " textChars={}",
                    content.length,
                    extracted.type(),
                    extracted.pageCount(),
                    extracted.warnings().size(),
                    extracted.text().length());
        } else {
            text = command.text().strip();
            sourceType = "TEXT";
            sourceRef = null;
        }

        if (text.length() > templateProperties.getMaxTextChars()) {
            throw ApiException.unprocessable(
                    "TEXT_TOO_LONG",
                    "Text is longer than " + templateProperties.getMaxTextChars() + " characters");
        }

        ExtractedTemplate extracted;
        try {
            extracted =
                    templateExtractionService.extract(
                            text, specialization.getId(), level.getId(), llmProperties.getModel());
        } catch (LlmAuthException
                | LlmNotConfiguredException
                | LlmQuotaException
                | LlmUnavailableException e) {
            // Решение команды (RFC-002, вопрос 17.3): если LLM недоступна, вакансия
            // создаётся с пустым шаблоном, HR заполняет требования вручную.
            log.warn(
                    "LLM is unavailable, vacancy will be created with an empty template:"
                            + " code={}, textChars={}, sourceType={}, cause={}",
                    e.errorCode(),
                    text.length(),
                    sourceType,
                    LogSupport.describe(e));
            extracted =
                    new ExtractedTemplate(
                            null, List.of(), null, llmProperties.getPromptVersion(), 0);
        } catch (LlmInvalidResponseException | LlmTruncatedException e) {
            log.warn(
                    "LLM returned unusable response, vacancy is not created: code={},"
                            + " textChars={}, cause={}",
                    e.errorCode(),
                    text.length(),
                    LogSupport.describe(e));
            throw e;
        }

        String title = command.title();
        if (title == null || title.isBlank()) {
            title = extracted.title();
        }
        log.info(
                "Vacancy create resolved: specialization={}, level={}, sourceType={},"
                        + " textChars={}, requirements={}, dropped={}, model={}",
                specialization.getCode(),
                level.getCode(),
                sourceType,
                text.length(),
                extracted.requirements().size(),
                extracted.droppedRequirements(),
                extracted.model());
        VacancyView view =
                store.create(text, sourceType, sourceRef, title, specialization, level, extracted);
        log.info(
                "Vacancy created: id={}, version={}, status={}, requirements={}, textChars={}",
                view.id(),
                view.version(),
                view.status(),
                view.requirements().size(),
                text.length());
        return view;
    }

    /**
     * Для IT-специализации уровень обязателен: грейд определяет стартовые гиперпараметры. Для
     * остальных специализаций грейда нет, переданный уровень игнорируется.
     */
    private LevelEntity resolveLevel(SpecializationEntity specialization, String code) {
        if (!specialization.isIt()) {
            return hiddenLevel();
        }
        if (code == null || code.isBlank() || HIDDEN_LEVEL.equals(code)) {
            throw ApiException.unprocessable(
                    "LEVEL_REQUIRED", "level is required for IT specialization");
        }
        return levelRepository
                .findByCodeAndActiveTrue(code)
                .orElseThrow(
                        () ->
                                ApiException.unprocessable(
                                        "UNKNOWN_LEVEL", "Unknown level: " + code));
    }

    private LevelEntity hiddenLevel() {
        return levelRepository
                .findByCode(HIDDEN_LEVEL)
                .orElseThrow(() -> new IllegalStateException("Hidden level 'none' is not seeded"));
    }

    public VacancyView get(UUID id) {
        VacancyEntity vacancy =
                vacancyRepository
                        .findById(id)
                        .orElseThrow(() -> ApiException.notFound("Vacancy not found"));
        VacancyView view = assembler.assemble(vacancy);
        log.debug(
                "Vacancy read: id={}, status={}, version={}, requirements={}",
                view.id(),
                view.status(),
                view.version(),
                view.requirements().size());
        return view;
    }

    public VacancyPage list(Integer limit, Integer offset) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(200, limit));
        int safeOffset = offset == null ? 0 : Math.max(0, offset);
        VacancyPage page = assembler.list(safeLimit, safeOffset);
        log.debug(
                "Vacancies listed: limit={}, offset={}, returned={}, total={}",
                safeLimit,
                safeOffset,
                page.items().size(),
                page.total());
        return page;
    }

    public VacancyView update(UUID id, UpdateTemplateCommand command) {
        log.info(
                "Vacancy template update requested: id={}, baseVersion={}, specialization={},"
                        + " level={}, requirements={}",
                id,
                command.baseVersion(),
                command.specialization(),
                command.level(),
                command.requirements() == null ? 0 : command.requirements().size());
        VacancyView view = store.update(id, command);
        log.info(
                "Vacancy template updated: id={}, version={}, status={}, confirmed={},"
                        + " requirements={}",
                view.id(),
                view.version(),
                view.status(),
                view.confirmed(),
                view.requirements().size());
        return view;
    }

    public void delete(UUID id) {
        store.delete(id);
        log.info("Vacancy deleted: id={}", id);
    }

    public List<SpecializationItem> specializations() {
        return assembler.listSpecializations();
    }

    public List<ReferenceItem> levels() {
        return assembler.listLevels();
    }
}
