package ats.resume.infrastructure.pii.sanitize;

import ats.resume.config.SanitizeProperties;
import ats.resume.domain.privacy.PiiDetector;
import ats.resume.domain.privacy.PiiMaskingException;
import ats.resume.domain.privacy.PiiSpan;
import ats.sanitize.proto.DetectPiiRequest;
import ats.sanitize.proto.DetectPiiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Шлюз к локальному sanitize-service. Сервис отдаёт только сущности; плейсхолдеры, vault и решение
 * о дальнейшей передаче текста остаются в Java.
 *
 * <p>По умолчанию используется spaCy-слой sanitize-service. LLM-слой добавляется конфигурацией
 * сервиса без изменения этого класса.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ats.pii.sanitize", name = "enabled", havingValue = "true")
public class SanitizePiiDetector implements PiiDetector {

    private final SanitizeClient client;
    private final SanitizeProperties properties;

    @Override
    public List<PiiSpan> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        DetectPiiRequest.Builder request = DetectPiiRequest.newBuilder().setText(text);
        if (!properties.getPipeline().isBlank()) {
            request.setPipeline(properties.getPipeline());
        }
        if (!properties.getLayers().isEmpty()) {
            request.addAllLayers(properties.getLayers());
        }
        if (properties.getMinConfidence() > 0) {
            request.setMinConfidence(properties.getMinConfidence());
        }

        long startedAt = System.nanoTime();
        DetectPiiResponse response;
        try {
            response = client.detect(request.build());
        } catch (RuntimeException e) {
            log.warn(
                    "sanitize_pii_failed fail_closed={} error={}",
                    properties.isFailClosed(),
                    e.getClass().getSimpleName());
            if (properties.isFailClosed()) {
                throw new PiiMaskingException("sanitize-service is unavailable", e);
            }
            return List.of();
        }

        List<PiiSpan> spans = SanitizeSpanMapper.toSpans(text, response.getEntitiesList());
        log.info(
                "sanitize_pii_detected pipeline={} layers={} model={} model_version={} entities={} spans={} elapsed_ms={}",
                response.getPipeline(),
                response.getLayersList(),
                response.getModel(),
                response.getModelVersion(),
                response.getEntitiesCount(),
                spans.size(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return spans;
    }
}
