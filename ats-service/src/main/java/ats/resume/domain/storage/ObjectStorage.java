package ats.resume.domain.storage;

/** Объектное хранилище оригиналов. PII в ключах и метаданных не хранится. */
public interface ObjectStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void delete(String key);
}
