package ats.resume.infrastructure.pii;

import ats.resume.domain.privacy.PiiDetector;
import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Годы обучения в блоке образования — PII, даже если NER их не разметил. */
@Component
@RequiredArgsConstructor
public class EducationPiiDetector implements PiiDetector {

    private final EducationExtractor educationExtractor;

    @Override
    public List<PiiSpan> detect(String text) {
        List<PiiSpan> spans = new ArrayList<>();
        for (int[] span : educationExtractor.educationYearSpans(text)) {
            spans.add(
                    new PiiSpan(
                            span[0],
                            span[1],
                            PiiType.GRADUATION_YEAR,
                            1.0,
                            PiiSpan.Source.EDUCATION));
        }
        return spans;
    }
}
