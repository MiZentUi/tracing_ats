package ats.resume.domain.privacy;

import java.util.List;

/** Детектор одного слоя PII. Слои складываются, пересечения разрешает PiiMaskingService. */
public interface PiiDetector {

    List<PiiSpan> detect(String text);
}
