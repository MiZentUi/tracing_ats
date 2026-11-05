package ats.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ats.extraction.TestDocuments;
import ats.extraction.domain.FileType;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ContentFileTypeDetectorTest {

    private final ContentFileTypeDetector detector = new ContentFileTypeDetector();

    @Test
    void detectsPdfBySignature() throws Exception {
        assertThat(detector.detect("document.pdf", TestDocuments.pdf("hello"))).isEqualTo(FileType.PDF);
    }

    @Test
    void detectsDocxByZipCatalog() throws Exception {
        assertThat(detector.detect("document.docx", TestDocuments.docx("hello")))
                .isEqualTo(FileType.DOCX);
    }

    @Test
    void detectsPlainText() {
        assertThat(detector.detect("resume.txt", bytes("hello")))
                .isEqualTo(FileType.TEXT);
    }

    @Test
    void detectsMarkdownByExtension() {
        assertThat(detector.detect("notes.md", bytes("# hello")))
                .isEqualTo(FileType.MARKDOWN);
    }

    @Test
    void detectsMarkdownByLongExtension() {
        assertThat(detector.detect("notes.markdown", bytes("# hello")))
                .isEqualTo(FileType.MARKDOWN);
    }

    @Test
    void ignoresTextExtensionForPdfContent() throws Exception {
        assertThat(detector.detect("document.txt", TestDocuments.pdf("hello")))
                .isEqualTo(FileType.PDF);
    }

    @Test
    void rejectsExecutableRenamedToPdf() {
        ExtractionException exception = catchThrowableOfType(
                () -> detector.detect("resume.pdf", new byte[] {0x4D, 0x5A, 0x00, 0x00}),
                ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsXlsxRenamedToDocx() throws Exception {
        ExtractionException exception = catchThrowableOfType(
                () -> detector.detect("resume.docx", TestDocuments.xlsx()),
                ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_TYPE);
    }

    @Test
    void rejectsLegacyDoc() {
        byte[] ole2 = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1,
            0x00
        };

        ExtractionException exception = catchThrowableOfType(
                () -> detector.detect("resume.doc", ole2), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_TYPE);
        assertThat(exception.getMessage()).contains("DOCX");
    }

    @Test
    void rejectsTextContentWithDocxExtension() {
        ExtractionException exception = catchThrowableOfType(
                () -> detector.detect("resume.docx", bytes("just text")), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_TYPE);
    }

    @Test
    void treatsEmptyFileAsText() {
        assertThat(detector.detect("empty.txt", new byte[0])).isEqualTo(FileType.TEXT);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
