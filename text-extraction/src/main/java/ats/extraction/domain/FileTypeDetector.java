package ats.extraction.domain;

/**
 * Определяет реальный формат файла по содержимому. Имя файла — только подсказка для различения
 * текстовых форматов и проверки явных расхождений.
 */
public interface FileTypeDetector {

    FileType detect(String fileName, byte[] content);
}
