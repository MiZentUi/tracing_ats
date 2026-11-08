package ats.extraction.domain;

public interface TextExtractionService {

    /**
     * Извлекает и нормализует текст из файла.
     *
     * @throws ats.extraction.domain.error.ExtractionException если файл не прошёл проверку
     */
    ExtractedText extract(UploadedFile file);
}
