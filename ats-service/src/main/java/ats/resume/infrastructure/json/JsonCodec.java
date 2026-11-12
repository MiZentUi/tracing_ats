package ats.resume.infrastructure.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/** Единая точка JSON для полей БД. В логи содержимое не попадает. */
@Component
public class JsonCodec {

    private final ObjectMapper mapper =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize JSON", e);
        }
    }

    public <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot deserialize JSON", e);
        }
    }

    public <T> List<T> readList(String value, TypeReference<List<T>> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot deserialize JSON list", e);
        }
    }

    public <T> T read(String value, TypeReference<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot deserialize JSON", e);
        }
    }
}
