package ats.resume.infrastructure.pii.sanitize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ats.resume.config.SanitizeProperties;
import ats.resume.domain.privacy.PiiMaskingException;
import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import ats.sanitize.proto.DetectPiiRequest;
import ats.sanitize.proto.DetectPiiResponse;
import ats.sanitize.proto.Layer;
import ats.sanitize.proto.PiiEntity;
import ats.sanitize.proto.PiiEntityType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SanitizePiiDetectorTest {

    @Test
    void sendsConfiguredPipelineLayersAndThreshold() {
        SanitizeProperties properties = new SanitizeProperties();
        properties.setPipeline("ru");
        properties.setLayers(List.of(Layer.LAYER_SPACY));
        properties.setMinConfidence(0.4);

        AtomicReference<DetectPiiRequest> captured = new AtomicReference<>();
        SanitizeClient client =
                request -> {
                    captured.set(request);
                    return response(
                            PiiEntity.newBuilder()
                                    .setType(PiiEntityType.PII_TYPE_NAME)
                                    .setStart(0)
                                    .setEnd(4)
                                    .setConfidence(0.7)
                                    .setLayer(Layer.LAYER_SPACY)
                                    .build());
                };

        List<PiiSpan> spans = new SanitizePiiDetector(client, properties).detect("Иван");

        assertThat(captured.get().getText()).isEqualTo("Иван");
        assertThat(captured.get().getPipeline()).isEqualTo("ru");
        assertThat(captured.get().getLayersList()).containsExactly(Layer.LAYER_SPACY);
        assertThat(captured.get().getMinConfidence()).isEqualTo(0.4);
        assertThat(spans)
                .singleElement()
                .satisfies(
                        span -> {
                            assertThat(span.type()).isEqualTo(PiiType.NAME);
                            assertThat(span.source()).isEqualTo(PiiSpan.Source.SANITIZE);
                        });
    }

    @Test
    void doesNotCallServiceForBlankText() {
        SanitizeProperties properties = new SanitizeProperties();
        SanitizeClient client =
                request -> {
                    throw new AssertionError("sanitize-service must not be called");
                };

        assertThat(new SanitizePiiDetector(client, properties).detect("   ")).isEmpty();
    }

    @Test
    void failClosedThrowsWhenServiceIsUnavailable() {
        SanitizeProperties properties = new SanitizeProperties();
        properties.setFailClosed(true);
        SanitizeClient client =
                request -> {
                    throw new IllegalStateException("connection refused");
                };

        assertThatThrownBy(() -> new SanitizePiiDetector(client, properties).detect("Иван"))
                .isInstanceOf(PiiMaskingException.class)
                .hasMessageContaining("sanitize-service");
    }

    @Test
    void failOpenReturnsEmptyWhenServiceIsUnavailable() {
        SanitizeProperties properties = new SanitizeProperties();
        properties.setFailClosed(false);
        SanitizeClient client =
                request -> {
                    throw new IllegalStateException("connection refused");
                };

        assertThat(new SanitizePiiDetector(client, properties).detect("Иван")).isEmpty();
    }

    private static DetectPiiResponse response(PiiEntity... entities) {
        return DetectPiiResponse.newBuilder()
                .addAllEntities(List.of(entities))
                .setPipeline("ru")
                .addLayers(Layer.LAYER_SPACY)
                .setModel("spacy:ru_core_news_md")
                .setModelVersion("3.8.0")
                .build();
    }
}
