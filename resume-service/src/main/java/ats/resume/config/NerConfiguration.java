package ats.resume.config;

import ats.resume.infrastructure.pii.ner.NerModel;
import ats.resume.infrastructure.pii.ner.NoOpNerModel;
import ats.resume.infrastructure.pii.ner.OnnxNerModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Локальная NER-модель. В CI и локальной разработке — fake, в production — ONNX и fail-closed.
 * Модель не ходит в сеть и не может снять маску, только добавить.
 */
@Configuration(proxyBeanMethods = false)
public class NerConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "ats.pii.ner",
            name = "provider",
            havingValue = "fake",
            matchIfMissing = true)
    NerModel fakeNerModel() {
        return new NoOpNerModel();
    }

    @Bean
    @ConditionalOnProperty(prefix = "ats.pii.ner", name = "provider", havingValue = "onnx")
    NerModel onnxNerModel(PiiProperties properties) {
        return new OnnxNerModel(properties.getNer());
    }
}
