package ats.resume.infrastructure.pii.ner;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Заглушка для CI и локальной разработки без скачанной модели. Не маскирует семантические PII. */
@Slf4j
public class NoOpNerModel implements NerModel {

    @Override
    public List<NerEntity> detect(String text) {
        return List.of();
    }

    @Override
    public String modelName() {
        return "fake-ner";
    }

    @Override
    public String modelHash() {
        return "fake";
    }
}
