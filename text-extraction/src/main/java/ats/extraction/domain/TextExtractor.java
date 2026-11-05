package ats.extraction.domain;

import java.util.Set;

/**
 * Порт извлечения для одного или нескольких форматов. Реализации не нормализуют текст:
 * это делает {@link TextNormalizer} после извлечения.
 */
public interface TextExtractor {

    Set<FileType> supportedTypes();

    RawExtraction extract(byte[] content);
}
