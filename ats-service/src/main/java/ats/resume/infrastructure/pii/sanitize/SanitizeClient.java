package ats.resume.infrastructure.pii.sanitize;

import ats.sanitize.proto.DetectPiiRequest;
import ats.sanitize.proto.DetectPiiResponse;

/**
 * Транспортная граница sanitize-service. Таймауты и gRPC-логирование живут здесь, маппинг в
 * доменные спаны — в {@link SanitizePiiDetector}.
 */
public interface SanitizeClient {

    DetectPiiResponse detect(DetectPiiRequest request);
}
