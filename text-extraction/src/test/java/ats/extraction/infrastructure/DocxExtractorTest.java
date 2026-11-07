package ats.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ats.extraction.TestDocuments;
import ats.extraction.domain.RawExtraction;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class DocxExtractorTest {

    @AfterEach
    void resetZipProtectionToDefaults() {
        new DocxExtractor(new ExtractionProperties());
    }

    @Test
    void extractsParagraphs() throws Exception {
        DocxExtractor extractor = new DocxExtractor(new ExtractionProperties());

        RawExtraction result = extractor.extract(TestDocuments.docx("first", "second"));

        assertThat(result.text()).contains("first", "second");
        assertThat(result.pageCount()).isNull();
    }

    @Test
    void extractsTableCells() throws Exception {
        DocxExtractor extractor = new DocxExtractor(new ExtractionProperties());

        RawExtraction result = extractor.extract(TestDocuments.docxWithTable());

        assertThat(result.text()).contains("before table", "name", "value", "Python", "5 years");
    }

    @Test
    void rejectsCorruptedDocx() {
        DocxExtractor extractor = new DocxExtractor(new ExtractionProperties());
        byte[] corrupted = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02};

        ExtractionException exception =
                catchThrowableOfType(() -> extractor.extract(corrupted), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CORRUPTED_FILE);
    }

    @Test
    void rejectsEntryLargerThanConfiguredLimit() throws Exception {
        ExtractionProperties properties = new ExtractionProperties();
        properties.getDocx().setMaxEntrySize(DataSize.ofBytes(1));
        DocxExtractor extractor = new DocxExtractor(properties);
        byte[] docx = TestDocuments.docx("text that is definitely longer than one byte");

        ExtractionException exception =
                catchThrowableOfType(() -> extractor.extract(docx), ExtractionException.class);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CORRUPTED_FILE);
    }
}
