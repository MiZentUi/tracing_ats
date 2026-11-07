package ats.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ats.extraction.TestDocuments;
import ats.extraction.domain.RawExtraction;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    @Test
    void extractsTextAndPageCount() throws Exception {
        PdfTextExtractor extractor = extractorWithPageLimit(10);

        RawExtraction result = extractor.extract(TestDocuments.pdf("Hello PDF"));

        assertThat(result.text()).contains("Hello PDF");
        assertThat(result.pageCount()).isEqualTo(1);
    }

    @Test
    void returnsBlankTextForPdfWithoutTextLayer() throws Exception {
        PdfTextExtractor extractor = extractorWithPageLimit(10);

        RawExtraction result = extractor.extract(TestDocuments.emptyPdf());

        assertThat(result.text()).isBlank();
    }

    @Test
    void rejectsPasswordProtectedPdf() throws Exception {
        PdfTextExtractor extractor = extractorWithPageLimit(10);

        ExtractionException exception =
                catchThrowableOfType(
                        () -> extractor.extract(TestDocuments.passwordProtectedPdf("secret")),
                        ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENCRYPTED_FILE);
    }

    @Test
    void rejectsCorruptedPdf() {
        PdfTextExtractor extractor = extractorWithPageLimit(10);
        byte[] corrupted = "%PDF-1.7\nthis is not a real pdf".getBytes(StandardCharsets.US_ASCII);

        ExtractionException exception =
                catchThrowableOfType(() -> extractor.extract(corrupted), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CORRUPTED_FILE);
    }

    @Test
    void rejectsPdfWithTooManyPages() throws Exception {
        PdfTextExtractor extractor = extractorWithPageLimit(1);
        byte[] pdf = TestDocuments.pdf("page", 2);

        ExtractionException exception =
                catchThrowableOfType(() -> extractor.extract(pdf), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.LIMIT_EXCEEDED);
    }

    private static PdfTextExtractor extractorWithPageLimit(int maxPages) {
        ExtractionProperties properties = new ExtractionProperties();
        properties.setMaxPdfPages(maxPages);
        return new PdfTextExtractor(properties);
    }
}
