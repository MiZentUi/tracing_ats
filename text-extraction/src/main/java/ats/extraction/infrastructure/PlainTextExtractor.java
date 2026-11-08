package ats.extraction.infrastructure;

import ats.extraction.domain.ExtractionWarning;
import ats.extraction.domain.FileType;
import ats.extraction.domain.RawExtraction;
import ats.extraction.domain.TextExtractor;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PlainTextExtractor implements TextExtractor {

    private static final Set<FileType> SUPPORTED_TYPES = Set.of(FileType.TEXT, FileType.MARKDOWN);

    @Override
    public Set<FileType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public RawExtraction extract(byte[] content) {
        TextDecoder.Decoded decoded = TextDecoder.decode(content);
        List<ExtractionWarning> warnings =
                decoded.warning() == null ? List.of() : List.of(decoded.warning());
        return new RawExtraction(decoded.text(), null, warnings);
    }
}
