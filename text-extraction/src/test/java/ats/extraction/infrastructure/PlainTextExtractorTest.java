package ats.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ats.extraction.domain.ExtractionWarning;
import ats.extraction.domain.RawExtraction;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PlainTextExtractorTest {

    private final PlainTextExtractor extractor = new PlainTextExtractor();

    @Test
    void decodesUtf8() {
        RawExtraction result = extractor.extract("Привет".getBytes(StandardCharsets.UTF_8));

        assertThat(result.text()).isEqualTo("Привет");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void skipsUtf8Bom() {
        byte[] text = "Привет".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[text.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(text, 0, withBom, 3, text.length);

        assertThat(extractor.extract(withBom).text()).isEqualTo("Привет");
    }

    @Test
    void decodesUtf16LeWithBom() {
        byte[] text = "Привет".getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[text.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(text, 0, withBom, 2, text.length);

        assertThat(extractor.extract(withBom).text()).isEqualTo("Привет");
    }

    @Test
    void fallsBackToWindows1251WithWarning() {
        byte[] text = "Привет".getBytes(Charset.forName("windows-1251"));

        RawExtraction result = extractor.extract(text);

        assertThat(result.text()).isEqualTo("Привет");
        assertThat(result.warnings()).containsExactly(ExtractionWarning.ENCODING_FALLBACK_CP1251);
    }

    @Test
    void supportsTextAndMarkdown() {
        assertThat(extractor.supportedTypes())
                .containsExactlyInAnyOrder(
                        ats.extraction.domain.FileType.TEXT, ats.extraction.domain.FileType.MARKDOWN);
    }
}
