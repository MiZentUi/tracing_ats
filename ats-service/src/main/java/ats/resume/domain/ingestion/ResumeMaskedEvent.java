package ats.resume.domain.ingestion;

import java.util.UUID;

/** Резюме обезличено и закоммичено: можно считать предварительный скор. */
public record ResumeMaskedEvent(UUID applicationId) {}
