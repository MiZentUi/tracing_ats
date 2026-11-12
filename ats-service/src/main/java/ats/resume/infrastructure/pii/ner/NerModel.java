package ats.resume.infrastructure.pii.ner;

import java.util.List;

/**
 * Локальная NER-модель. Работает in-process, не ходит в сеть и не получает инструкций из текста
 * резюме. Может только добавлять маски, снимать существующие ей нельзя.
 */
public interface NerModel extends AutoCloseable {

    List<NerEntity> detect(String text);

    String modelName();

    String modelHash();

    @Override
    default void close() {}
}
