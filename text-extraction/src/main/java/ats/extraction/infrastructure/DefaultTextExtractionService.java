package ats.extraction.infrastructure;

import ats.extraction.domain.ExtractedText;
import ats.extraction.domain.FileType;
import ats.extraction.domain.FileTypeDetector;
import ats.extraction.domain.RawExtraction;
import ats.extraction.domain.TextExtractionService;
import ats.extraction.domain.TextExtractor;
import ats.extraction.domain.TextNormalizer;
import ats.extraction.domain.UploadedFile;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import jakarta.annotation.PreDestroy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultTextExtractionService implements TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTextExtractionService.class);

    private final FileTypeDetector detector;
    private final Map<FileType, TextExtractor> extractors;
    private final TextNormalizer normalizer;
    private final ExtractionProperties properties;
    private final ExecutorService executor;

    public DefaultTextExtractionService(
            FileTypeDetector detector,
            List<TextExtractor> extractors,
            TextNormalizer normalizer,
            ExtractionProperties properties) {
        this.detector = detector;
        this.extractors = buildRegistry(extractors);
        this.normalizer = normalizer;
        this.properties = properties;
        this.executor = Executors.newFixedThreadPool(properties.getPoolSize(), runnable -> {
            Thread thread = new Thread(runnable, "text-extraction");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public ExtractedText extract(UploadedFile file) {
        Objects.requireNonNull(file, "file");
        byte[] content = file.content();

        if (content.length > properties.getMaxFileSize().toBytes()) {
            throw new ExtractionException(
                    ErrorCode.FILE_TOO_LARGE,
                    "File is larger than " + properties.getMaxFileSize());
        }

        FileType type = detector.detect(file.fileName(), content);
        TextExtractor extractor = extractors.get(type);
        if (extractor == null) {
            throw new ExtractionException(
                    ErrorCode.UNSUPPORTED_TYPE, "No extractor registered for " + type);
        }

        long startedAt = System.nanoTime();
        RawExtraction raw = runWithTimeout(extractor, content);
        String text = normalizer.normalize(raw.text());

        if (text.isBlank()) {
            throw new ExtractionException(ErrorCode.TEXT_EMPTY, "No text found in file");
        }
        if (text.length() > properties.getMaxTextChars()) {
            throw new ExtractionException(
                    ErrorCode.LIMIT_EXCEEDED,
                    "Extracted text is longer than " + properties.getMaxTextChars() + " characters");
        }

        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
        log.debug(
                "Text extracted: type={}, bytes={}, chars={}, durationMs={}",
                type,
                content.length,
                text.length(),
                durationMillis);

        return new ExtractedText(text, type, raw.pageCount(), raw.warnings());
    }

    private RawExtraction runWithTimeout(TextExtractor extractor, byte[] content) {
        Future<RawExtraction> future;
        try {
            future = executor.submit(() -> extractor.extract(content));
        } catch (RejectedExecutionException e) {
            throw new ExtractionException(
                    ErrorCode.EXTRACTION_TIMEOUT, "Text extraction pool is exhausted", e);
        }

        try {
            return future.get(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ExtractionException(ErrorCode.EXTRACTION_TIMEOUT, "Text extraction timed out", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ExtractionException(ErrorCode.EXTRACTION_TIMEOUT, "Text extraction interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ExtractionException extractionException) {
                throw extractionException;
            }
            throw new ExtractionException(ErrorCode.CORRUPTED_FILE, "Text extraction failed", cause);
        }
    }

    private static Map<FileType, TextExtractor> buildRegistry(List<TextExtractor> extractors) {
        EnumMap<FileType, TextExtractor> registry = new EnumMap<>(FileType.class);
        for (TextExtractor extractor : extractors) {
            for (FileType type : extractor.supportedTypes()) {
                TextExtractor previous = registry.putIfAbsent(type, extractor);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Multiple text extractors registered for " + type + ": "
                                    + previous.getClass().getName() + " and "
                                    + extractor.getClass().getName());
                }
            }
        }
        return Map.copyOf(registry);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
