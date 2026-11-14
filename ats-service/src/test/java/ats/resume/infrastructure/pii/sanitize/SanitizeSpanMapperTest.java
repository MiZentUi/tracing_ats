package ats.resume.infrastructure.pii.sanitize;

import static org.assertj.core.api.Assertions.assertThat;

import ats.resume.domain.privacy.PiiSpan;
import ats.resume.domain.privacy.PiiType;
import ats.sanitize.proto.PiiEntity;
import ats.sanitize.proto.PiiEntityType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SanitizeSpanMapperTest {

    @Test
    void convertsCodePointOffsetsToUtf16() {
        String text = "🎯 Иван";

        List<PiiSpan> spans =
                SanitizeSpanMapper.toSpans(
                        text,
                        List.of(
                                PiiEntity.newBuilder()
                                        .setType(PiiEntityType.PII_TYPE_NAME)
                                        .setStart(2)
                                        .setEnd(6)
                                        .setConfidence(0.7)
                                        .build()));

        assertThat(spans).hasSize(1);
        PiiSpan span = spans.getFirst();
        assertThat(span.type()).isEqualTo(PiiType.NAME);
        assertThat(span.source()).isEqualTo(PiiSpan.Source.SANITIZE);
        assertThat(text.substring(span.start(), span.end())).isEqualTo("Иван");
    }

    @Test
    void dropsUnspecifiedAndUnknownTypes() {
        String text = "Иван";

        List<PiiSpan> spans =
                SanitizeSpanMapper.toSpans(
                        text,
                        List.of(
                                PiiEntity.newBuilder()
                                        .setType(PiiEntityType.PII_TYPE_UNSPECIFIED)
                                        .setStart(0)
                                        .setEnd(4)
                                        .build()));

        assertThat(spans).isEmpty();
    }

    @Test
    void dropsOffsetsOutsideText() {
        String text = "Иван";

        List<PiiSpan> spans =
                SanitizeSpanMapper.toSpans(
                        text,
                        List.of(
                                PiiEntity.newBuilder()
                                        .setType(PiiEntityType.PII_TYPE_NAME)
                                        .setStart(0)
                                        .setEnd(5)
                                        .build()));

        assertThat(spans).isEmpty();
    }

    @Test
    void clampsConfidence() {
        String text = "Иван";

        List<PiiSpan> spans =
                SanitizeSpanMapper.toSpans(
                        text,
                        List.of(
                                PiiEntity.newBuilder()
                                        .setType(PiiEntityType.PII_TYPE_NAME)
                                        .setStart(0)
                                        .setEnd(4)
                                        .setConfidence(1.5)
                                        .build(),
                                PiiEntity.newBuilder()
                                        .setType(PiiEntityType.PII_TYPE_LOCATION)
                                        .setStart(0)
                                        .setEnd(4)
                                        .setConfidence(Double.NaN)
                                        .build()));

        assertThat(spans).extracting(PiiSpan::confidence).containsExactly(1.0, 0.0);
    }

    @Test
    void mapsEverySupportedType() {
        String text = "Иван";

        List<PiiSpan> spans =
                SanitizeSpanMapper.toSpans(
                        text,
                        List.of(
                                PiiEntity.newBuilder()
                                        .setType(PiiEntityType.PII_TYPE_EMAIL)
                                        .setStart(0)
                                        .setEnd(4)
                                        .build(),
                                PiiEntity.newBuilder()
                                        .setType(PiiEntityType.PII_TYPE_PHONE)
                                        .setStart(0)
                                        .setEnd(4)
                                        .build(),
                                PiiEntity.newBuilder()
                                        .setType(PiiEntityType.PII_TYPE_UNIVERSITY)
                                        .setStart(0)
                                        .setEnd(4)
                                        .build()));

        assertThat(spans)
                .extracting(PiiSpan::type)
                .containsExactly(PiiType.EMAIL, PiiType.PHONE, PiiType.UNIVERSITY);
    }
}
