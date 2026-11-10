package ats.resume.infrastructure.storage;

import ats.resume.domain.storage.ObjectStorage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** In-memory хранилище для тестов и локального прогона без MinIO. */
@Component
@ConditionalOnProperty(prefix = "ats.storage", name = "type", havingValue = "memory")
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] content, String contentType) {
        objects.put(key, content.clone());
    }

    @Override
    public byte[] get(String key) {
        byte[] value = objects.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Object not found");
        }
        return value.clone();
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
