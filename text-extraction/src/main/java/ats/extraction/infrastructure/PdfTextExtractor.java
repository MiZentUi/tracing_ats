package ats.extraction.infrastructure;

import ats.extraction.domain.FileType;
import ats.extraction.domain.RawExtraction;
import ats.extraction.domain.TextExtractor;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PdfTextExtractor implements TextExtractor {

    private static final Set<FileType> SUPPORTED_TYPES = Set.of(FileType.PDF);

    private final ExtractionProperties properties;

    @Override
    public Set<FileType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public RawExtraction extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount > properties.getMaxPdfPages()) {
                throw new ExtractionException(
                        ErrorCode.LIMIT_EXCEEDED,
                        "PDF has too many pages: "
                                + pageCount
                                + " > "
                                + properties.getMaxPdfPages());
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return new RawExtraction(stripper.getText(document), pageCount, List.of());
        } catch (InvalidPasswordException e) {
            throw new ExtractionException(ErrorCode.ENCRYPTED_FILE, "PDF is password protected", e);
        } catch (ExtractionException e) {
            throw e;
        } catch (IOException e) {
            throw new ExtractionException(ErrorCode.CORRUPTED_FILE, "Cannot read PDF", e);
        }
    }
}
