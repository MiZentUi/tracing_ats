package ats.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ats.extraction.domain.ExtractedText;
import ats.extraction.domain.ExtractionWarning;
import ats.extraction.domain.FileType;
import ats.extraction.domain.FileTypeDetector;
import ats.extraction.domain.RawExtraction;
import ats.extraction.domain.TextExtractor;
import ats.extraction.domain.TextNormalizer;
import ats.extraction.domain.UploadedFile;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

class DefaultTextExtractionServiceTest {

    private final TextNormalizer normalizer = new TextNormalizer();
    private DefaultTextExtractionService service;

    @AfterEach
    void shutdownService() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void normalizesTextAndReturnsMetadata() {
        TextExtractor extractor = extractor(
                FileType.TEXT,
                new RawExtraction("  hello\r\n\r\n\r\nworld  ", 2, List.of(ExtractionWarning.ENCODING_FALLBACK_CP1251)));
        service = service(properties(), detector(FileType.TEXT), extractor);

        ExtractedText result = service.extract(file("resume.txt", "ignored"));

        assertThat(result.text()).isEqualTo("hello\n\nworld");
        assertThat(result.type()).isEqualTo(FileType.TEXT);
        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.warnings()).containsExactly(ExtractionWarning.ENCODING_FALLBACK_CP1251);
    }

    @Test
    void rejectsFileLargerThanLimit() {
        ExtractionProperties properties = properties();
        properties.setMaxFileSize(DataSize.ofBytes(3));
        service = service(properties, detector(FileType.TEXT), extractor(FileType.TEXT, RawExtraction.text("ok")));

        ExtractionException exception = catchThrowableOfType(
                () -> service.extract(file("resume.txt", "four")), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void rejectsEmptyText() {
        service = service(
                properties(), detector(FileType.TEXT), extractor(FileType.TEXT, RawExtraction.text(" \n\t ")));

        ExtractionException exception = catchThrowableOfType(
                () -> service.extract(file("resume.txt", "ignored")), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.TEXT_EMPTY);
    }

    @Test
    void rejectsTextLongerThanLimit() {
        ExtractionProperties properties = properties();
        properties.setMaxTextChars(3);
        service = service(properties, detector(FileType.TEXT), extractor(FileType.TEXT, RawExtraction.text("hello")));

        ExtractionException exception = catchThrowableOfType(
                () -> service.extract(file("resume.txt", "ignored")), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.LIMIT_EXCEEDED);
    }

    @Test
    void returnsTimeoutWhenExtractorIsTooSlow() {
        ExtractionProperties properties = properties();
        properties.setTimeout(Duration.ofMillis(50));
        properties.setPoolSize(1);
        TextExtractor slowExtractor = extractor(
                FileType.TEXT,
                RawExtraction.text("late"),
                () -> {
                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return RawExtraction.text("late");
                });
        service = service(properties, detector(FileType.TEXT), slowExtractor);

        ExtractionException exception = catchThrowableOfType(
                () -> service.extract(file("resume.txt", "ignored")), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTRACTION_TIMEOUT);
    }

    @Test
    void rejectsDuplicateExtractorsForSameType() {
        TextExtractor first = extractor(FileType.TEXT, RawExtraction.text("first"));
        TextExtractor second = extractor(FileType.TEXT, RawExtraction.text("second"));

        IllegalStateException exception = catchThrowableOfType(
                () -> service(properties(), detector(FileType.TEXT), first, second),
                IllegalStateException.class);

        assertThat(exception).hasMessageContaining("Multiple text extractors");
    }

    @Test
    void propagatesDetectorError() {
        FileTypeDetector failingDetector = (fileName, content) -> {
            throw new ExtractionException(ErrorCode.UNSUPPORTED_TYPE, "unsupported");
        };
        service = service(properties(), failingDetector, extractor(FileType.TEXT, RawExtraction.text("ok")));

        ExtractionException exception = catchThrowableOfType(
                () -> service.extract(file("resume.bin", "ignored")), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_TYPE);
    }

    @Test
    void doesNotLogFileTextOrName() {
        service = service(
                properties(), detector(FileType.TEXT), extractor(FileType.TEXT, RawExtraction.text("secret text")));
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultTextExtractionService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);

        try {
            service.extract(file("private-resume.txt", "secret text"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }

        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list)
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .doesNotContain("secret text", "private-resume.txt"));
    }

    private DefaultTextExtractionService service(
            ExtractionProperties properties, FileTypeDetector detector, TextExtractor... extractors) {
        return new DefaultTextExtractionService(detector, List.of(extractors), normalizer, properties);
    }

    private static ExtractionProperties properties() {
        return new ExtractionProperties();
    }

    private static FileTypeDetector detector(FileType type) {
        return (fileName, content) -> type;
    }

    private static UploadedFile file(String name, String content) {
        return new UploadedFile(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static TextExtractor extractor(FileType type, RawExtraction result) {
        return extractor(type, result, () -> result);
    }

    private static TextExtractor extractor(
            FileType type, RawExtraction ignored, ExtractionSupplier supplier) {
        return new TextExtractor() {
            @Override
            public Set<FileType> supportedTypes() {
                return Set.of(type);
            }

            @Override
            public RawExtraction extract(byte[] content) {
                return supplier.get();
            }
        };
    }

    @FunctionalInterface
    private interface ExtractionSupplier {
        RawExtraction get();
    }
}
