package ats.extraction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;

public final class TestDocuments {

    private TestDocuments() {}

    public static byte[] pdf(String text) throws IOException {
        return pdf(text, 1);
    }

    public static byte[] pdf(String text, int pageCount) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int page = 0; page < pageCount; page++) {
                PDPage pdfPage = new PDPage();
                document.addPage(pdfPage);
                try (PDPageContentStream content = new PDPageContentStream(document, pdfPage)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(text);
                    content.endText();
                }
            }
            return save(document);
        }
    }

    public static byte[] emptyPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return save(document);
        }
    }

    public static byte[] passwordProtectedPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }

            AccessPermission permission = new AccessPermission();
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-password", "user-password", permission);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return save(document);
        }
    }

    public static byte[] docx(String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            return save(document);
        }
    }

    public static byte[] docxWithTable() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("before table");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("name");
            table.getRow(0).getCell(1).setText("value");
            table.getRow(1).getCell(0).setText("Python");
            table.getRow(1).getCell(1).setText("5 years");
            return save(document);
        }
    }

    public static byte[] xlsx() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("value");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] save(XWPFDocument document) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.write(output);
            return output.toByteArray();
        }
    }
}
