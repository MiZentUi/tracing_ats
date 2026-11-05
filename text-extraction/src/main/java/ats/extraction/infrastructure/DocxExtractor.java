package ats.extraction.infrastructure;

import ats.extraction.domain.FileType;
import ats.extraction.domain.RawExtraction;
import ats.extraction.domain.TextExtractor;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

@Component
public class DocxExtractor implements TextExtractor {

    private static final Set<FileType> SUPPORTED_TYPES = Set.of(FileType.DOCX);

    public DocxExtractor(ExtractionProperties properties) {
        ZipSecureFile.setMinInflateRatio(properties.getDocx().getMinInflateRatio());
        ZipSecureFile.setMaxEntrySize(properties.getDocx().getMaxEntrySize().toBytes());
    }

    @Override
    public Set<FileType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public RawExtraction extract(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(text, paragraph);
                } else if (element instanceof XWPFTable table) {
                    appendTable(text, table);
                }
            }
            return new RawExtraction(text.toString(), null, List.of());
        } catch (IOException | RuntimeException e) {
            throw new ExtractionException(ErrorCode.CORRUPTED_FILE, "Cannot read DOCX", e);
        }
    }

    private static void appendParagraph(StringBuilder text, XWPFParagraph paragraph) {
        String value = paragraph.getText();
        if (value != null && !value.isBlank()) {
            text.append(value).append('\n');
        }
    }

    private static void appendTable(StringBuilder text, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder rowText = new StringBuilder();
            for (XWPFTableCell cell : row.getTableCells()) {
                String value = cell.getText();
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (!rowText.isEmpty()) {
                    rowText.append('\t');
                }
                rowText.append(value.strip());
            }
            if (!rowText.isEmpty()) {
                text.append(rowText).append('\n');
            }
        }
    }
}
