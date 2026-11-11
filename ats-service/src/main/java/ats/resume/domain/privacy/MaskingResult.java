package ats.resume.domain.privacy;

import ats.resume.domain.vault.PiiPayload;

/** Результат маскировки: текст для scoring и зашифрованный позже payload. */
public record MaskingResult(String maskedText, PiiPayload payload, EducationFacts education) {}
