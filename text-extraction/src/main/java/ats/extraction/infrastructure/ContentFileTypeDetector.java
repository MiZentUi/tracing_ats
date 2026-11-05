package ats.extraction.infrastructure;

import ats.extraction.domain.FileType;
import ats.extraction.domain.FileTypeDetector;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.springframework.stereotype.Component;

/**
 * Определяет формат по сигнатурам и структуре файла. Расширение используется только
 * как подсказка для Markdown и для проверки явных расхождений с PDF/DOCX.
 */
@Component
public class ContentFileTypeDetector implements FileTypeDetector {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_SIGNATURE = {
        (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    @Override
    public FileType detect(String fileName, byte[] content) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");

        FileType detected = detectByContent(content);
        if (detected == FileType.TEXT && isMarkdown(fileName)) {
            detected = FileType.MARKDOWN;
        }

        String extension = extension(fileName);
        if ("pdf".equals(extension) && detected != FileType.PDF) {
            throw new ExtractionException(
                    ErrorCode.UNSUPPORTED_TYPE, "File extension .pdf does not match its content");
        }
        if ("docx".equals(extension) && detected != FileType.DOCX) {
            throw new ExtractionException(
                    ErrorCode.UNSUPPORTED_TYPE, "File extension .docx does not match its content");
        }
        return detected;
    }

    private static FileType detectByContent(byte[] content) {
        if (startsWith(content, PDF_SIGNATURE)) {
            return FileType.PDF;
        }
        if (startsWith(content, OLE2_SIGNATURE)) {
            throw new ExtractionException(
                    ErrorCode.UNSUPPORTED_TYPE, "Legacy .doc is not supported; save the file as DOCX");
        }
        if (startsWith(content, ZIP_SIGNATURE)) {
            if (isDocx(content)) {
                return FileType.DOCX;
            }
            throw new ExtractionException(
                    ErrorCode.UNSUPPORTED_TYPE, "ZIP archive is not a DOCX document");
        }
        if (TextDecoder.isProbablyText(content)) {
            return FileType.TEXT;
        }
        throw new ExtractionException(ErrorCode.UNSUPPORTED_TYPE, "Unsupported file type");
    }

    private static boolean isDocx(byte[] content) {
        try (ZipFile zipFile = ZipFile.builder()
                .setSeekableByteChannel(new SeekableInMemoryByteChannel(content))
                .get()) {
            return zipFile.getEntry("[Content_Types].xml") != null
                    && zipFile.getEntry("word/document.xml") != null;
        } catch (IOException e) {
            throw new ExtractionException(ErrorCode.CORRUPTED_FILE, "Cannot read ZIP archive", e);
        }
    }

    private static boolean isMarkdown(String fileName) {
        String extension = extension(fileName);
        return "md".equals(extension) || "markdown".equals(extension);
    }

    private static String extension(String fileName) {
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String name = fileName.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
