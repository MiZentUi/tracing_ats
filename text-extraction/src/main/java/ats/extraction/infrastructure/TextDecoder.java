package ats.extraction.infrastructure;

import ats.extraction.domain.ExtractionWarning;
import ats.extraction.domain.error.ErrorCode;
import ats.extraction.domain.error.ExtractionException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Общая логика определения и декодирования текстовых файлов. Используется и детектором типа, и
 * текстовым экстрактором, чтобы правила не расходились.
 */
final class TextDecoder {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final double MIN_PRINTABLE_RATIO = 0.9d;

    private TextDecoder() {}

    record Decoded(String text, ExtractionWarning warning) {}

    static boolean isProbablyText(byte[] content) {
        if (content.length == 0) {
            return true;
        }
        if (hasBom(content)) {
            return true;
        }
        if (containsNul(content)) {
            return false;
        }
        try {
            decodeStrict(content, 0, content.length, StandardCharsets.UTF_8);
            return true;
        } catch (CharacterCodingException e) {
            String decodedAsCp1251 = new String(content, WINDOWS_1251);
            return printableRatio(decodedAsCp1251) >= MIN_PRINTABLE_RATIO;
        }
    }

    static Decoded decode(byte[] content) {
        if (startsWith(content, UTF8_BOM)) {
            return new Decoded(decodeBom(content, UTF8_BOM.length, StandardCharsets.UTF_8), null);
        }
        if (startsWith(content, UTF16_LE_BOM)) {
            return new Decoded(
                    decodeBom(content, UTF16_LE_BOM.length, StandardCharsets.UTF_16LE), null);
        }
        if (startsWith(content, UTF16_BE_BOM)) {
            return new Decoded(
                    decodeBom(content, UTF16_BE_BOM.length, StandardCharsets.UTF_16BE), null);
        }
        try {
            return new Decoded(
                    decodeStrict(content, 0, content.length, StandardCharsets.UTF_8), null);
        } catch (CharacterCodingException e) {
            return new Decoded(
                    new String(content, WINDOWS_1251), ExtractionWarning.ENCODING_FALLBACK_CP1251);
        }
    }

    private static String decodeBom(byte[] content, int offset, Charset charset) {
        try {
            return decodeStrict(content, offset, content.length - offset, charset);
        } catch (CharacterCodingException e) {
            throw new ExtractionException(ErrorCode.CORRUPTED_FILE, "Invalid text encoding", e);
        }
    }

    private static String decodeStrict(byte[] content, int offset, int length, Charset charset)
            throws CharacterCodingException {
        CharsetDecoder decoder =
                charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(content, offset, length)).toString();
    }

    private static boolean hasBom(byte[] content) {
        return startsWith(content, UTF8_BOM)
                || startsWith(content, UTF16_LE_BOM)
                || startsWith(content, UTF16_BE_BOM);
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

    private static boolean containsNul(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static double printableRatio(String text) {
        if (text.isEmpty()) {
            return 1d;
        }

        int printable = 0;
        int total = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            total++;
            if (codePoint == '\n'
                    || codePoint == '\r'
                    || codePoint == '\t'
                    || (!Character.isISOControl(codePoint) && codePoint != 0xFFFD)) {
                printable++;
            }
            i += Character.charCount(codePoint);
        }
        return (double) printable / total;
    }
}
