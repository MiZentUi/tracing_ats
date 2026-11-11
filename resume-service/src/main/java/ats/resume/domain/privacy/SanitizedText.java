package ats.resume.domain.privacy;

import java.util.List;

public record SanitizedText(String text, List<InjectionFlag> flags) {

    public SanitizedText {
        flags = List.copyOf(flags);
    }
}
