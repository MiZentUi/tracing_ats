package ats.resume.domain.vault;

import ats.resume.domain.privacy.PiiType;
import java.util.List;

/** То, что шифруется в pii_vault. В открытом виде никуда не уходит. */
public record PiiPayload(
        List<PiiEntry> entries,
        String originalFilename,
        String githubUrl,
        String university,
        Integer graduationYear) {

    public PiiPayload {
        entries = List.copyOf(entries);
    }

    public static PiiPayload empty() {
        return new PiiPayload(List.of(), null, null, null, null);
    }

    public record PiiEntry(PiiType type, String placeholder, String value) {}
}
