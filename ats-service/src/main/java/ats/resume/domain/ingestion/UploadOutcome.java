package ats.resume.domain.ingestion;

import java.util.List;
import java.util.UUID;

/** Результат загрузки одного файла. Не содержит PII. */
public record UploadOutcome(UUID resumeId, Status status, List<String> warnings, UUID batchId) {

    public UploadOutcome {
        warnings = List.copyOf(warnings);
    }

    public UploadOutcome(UUID resumeId, Status status, List<String> warnings) {
        this(resumeId, status, warnings, null);
    }

    public enum Status {
        MASKED,
        TEXT_EMPTY,
        MASKING_FAILED,
        FAILED
    }
}
