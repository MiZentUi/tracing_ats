package ats.vacancy.dev;

import ats.extraction.domain.ExtractedText;
import ats.extraction.domain.TextExtractionService;
import ats.extraction.domain.UploadedFile;
import ats.extraction.domain.error.ExtractionException;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Временный dev-эндпоинт для ручной проверки извлечения текста.
 * Удалить после проверки; в RFC контроллер не входит.
 */
@RestController
@ConditionalOnProperty(
        prefix = "ats.dev.extraction-endpoint",
        name = "enabled",
        havingValue = "true")
@RequestMapping("/api/v1/dev/extraction")
public class DevExtractionController {

    private final TextExtractionService textExtractionService;

    public DevExtractionController(TextExtractionService textExtractionService) {
        this.textExtractionService = textExtractionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtractionResponse extract(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot read uploaded file", e);
        }

        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "upload");
        ExtractedText result = textExtractionService.extract(new UploadedFile(fileName, content));

        return new ExtractionResponse(
                result.type().name(),
                result.pageCount(),
                result.warnings(),
                result.text().length(),
                result.text());
    }

    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<ExtractionError> handleExtractionException(ExtractionException exception) {
        return ResponseEntity.status(exception.errorCode().httpStatus())
                .body(new ExtractionError(
                        exception.errorCode().name(), exception.getMessage()));
    }

    public record ExtractionResponse(
            String type,
            Integer pageCount,
            List<ats.extraction.domain.ExtractionWarning> warnings,
            int charCount,
            String text) {}

    public record ExtractionError(String code, String message) {}
}
