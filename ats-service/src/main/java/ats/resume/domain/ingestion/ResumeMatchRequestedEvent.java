package ats.resume.domain.ingestion;

import java.util.UUID;

/** Резюме загружено без вакансии: нужно сравнить его со всеми вакансиями. */
public record ResumeMatchRequestedEvent(UUID batchId, UUID resumeId) {}
