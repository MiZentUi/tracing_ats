package ats.resume.infrastructure.pii.ner;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.tokenizers.jni.CharSpan;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ats.resume.config.PiiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * ONNX-инференс NER в JVM. Модель не коммитится: путь задаётся конфигом, хеш фиксируется в аудите.
 * Поддерживаются BIO/BIOUL-схемы PER/LOC/ORG.
 */
@Slf4j
public class OnnxNerModel implements NerModel {

    private static final int MAX_CHARS_PER_CHUNK = 1200;
    private static final int CHUNK_OVERLAP = 200;

    private final OrtEnvironment environment;
    private final OrtSession.SessionOptions sessionOptions;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Map<Integer, String> labels;
    private final double confidenceThreshold;
    private final String modelName;
    private final String modelHash;

    public OnnxNerModel(PiiProperties.Ner properties) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.sessionOptions = new OrtSession.SessionOptions();
            this.session =
                    environment.createSession(properties.getModelPath().toString(), sessionOptions);
            this.tokenizer =
                    HuggingFaceTokenizer.newInstance(
                            properties.getTokenizerPath(),
                            Map.of(
                                    "padding", "false",
                                    "truncation", "true",
                                    "maxLength", "512"));
            this.labels = loadLabels(properties.getConfigPath());
            this.confidenceThreshold = properties.getConfidenceThreshold();
            this.modelName = properties.getModelPath().getFileName().toString();
            this.modelHash = sha256(properties.getModelPath());
            log.info(
                    "Local NER model loaded: name={}, hash={}, labels={}",
                    modelName,
                    modelHash,
                    labels.size());
        } catch (OrtException | IOException e) {
            throw new IllegalStateException("Cannot load local NER model", e);
        }
    }

    @Override
    public List<NerEntity> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<NerEntity> entities = new ArrayList<>();
        for (int[] chunk : chunks(text)) {
            entities.addAll(detectChunk(text.substring(chunk[0], chunk[1]), chunk[0]));
        }
        return merge(entities);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public String modelHash() {
        return modelHash;
    }

    @Override
    public void close() {
        tokenizer.close();
        try {
            session.close();
            sessionOptions.close();
        } catch (OrtException e) {
            log.warn("Cannot close ONNX session", e);
        }
    }

    private List<NerEntity> detectChunk(String chunk, int offset) {
        Encoding encoding = tokenizer.encode(chunk);
        long[] ids = encoding.getIds();
        long[] attention = encoding.getAttentionMask();
        long[] typeIds = encoding.getTypeIds();
        long[] special = encoding.getSpecialTokenMask();
        CharSpan[] charSpans = encoding.getCharTokenSpans();
        if (ids.length == 0 || charSpans == null) {
            return List.of();
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", tensor(ids));
            inputs.put("attention_mask", tensor(attention));
            if (session.getInputNames().contains("token_type_ids")) {
                inputs.put(
                        "token_type_ids", tensor(typeIds == null ? new long[ids.length] : typeIds));
            }

            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] logits = (float[][][]) result.get(0).getValue();
                return decode(logits[0], charSpans, special, offset);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Local NER inference failed", e);
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private List<NerEntity> decode(
            float[][] tokenLogits, CharSpan[] charSpans, long[] special, int offset) {
        List<NerEntity> entities = new ArrayList<>();
        EntityBuilder current = null;

        for (int index = 0; index < tokenLogits.length; index++) {
            if (special != null && index < special.length && special[index] != 0) {
                current = close(entities, current);
                continue;
            }
            if (index >= charSpans.length) {
                break;
            }
            CharSpan span = charSpans[index];
            int start = span.getStart();
            int end = span.getEnd();
            if (start < 0 || end <= start) {
                continue;
            }

            int best = argmax(tokenLogits[index]);
            double confidence = probability(tokenLogits[index], best);
            String label = labels.getOrDefault(best, "O");
            if ("O".equalsIgnoreCase(label) || confidence < confidenceThreshold) {
                current = close(entities, current);
                continue;
            }

            String prefix = label.length() > 2 ? label.substring(0, 2) : "";
            String type = normalizeType(label.substring(Math.min(2, label.length())));
            if (type == null) {
                current = close(entities, current);
                continue;
            }

            boolean begins =
                    prefix.equalsIgnoreCase("B-")
                            || prefix.equalsIgnoreCase("S-")
                            || prefix.equalsIgnoreCase("U-");
            boolean inside =
                    prefix.equalsIgnoreCase("I-")
                            || prefix.equalsIgnoreCase("L-")
                            || prefix.equalsIgnoreCase("E-");

            if (begins || current == null || !current.type.equals(type)) {
                current = close(entities, current);
                current = new EntityBuilder(type, offset + start, offset + end, confidence);
            } else if (inside) {
                current.end = offset + end;
                current.confidence = Math.min(current.confidence, confidence);
            }
        }
        close(entities, current);

        return entities.stream().filter(entity -> entity.end() - entity.start() >= 2).toList();
    }

    private EntityBuilder close(List<NerEntity> entities, EntityBuilder current) {
        if (current != null) {
            String value = current.type;
            int start = current.start;
            int end = current.end;
            if (end > start) {
                entities.add(new NerEntity(value, start, end, current.confidence));
            }
        }
        return null;
    }

    private OnnxTensor tensor(long[] values) throws OrtException {
        return OnnxTensor.createTensor(
                environment, LongBuffer.wrap(values), new long[] {1, values.length});
    }

    private int argmax(float[] values) {
        int best = 0;
        for (int index = 1; index < values.length; index++) {
            if (values[index] > values[best]) {
                best = index;
            }
        }
        return best;
    }

    private double probability(float[] values, int index) {
        float max = values[0];
        for (float value : values) {
            max = Math.max(max, value);
        }
        double sum = 0;
        for (float value : values) {
            sum += Math.exp(value - max);
        }
        return Math.exp(values[index] - max) / sum;
    }

    private String normalizeType(String rawType) {
        String type = rawType.toUpperCase(Locale.ROOT);
        if (type.startsWith("PER") || type.contains("NAME")) {
            return NerEntity.PERSON;
        }
        if (type.startsWith("LOC")
                || type.contains("CITY")
                || type.contains("COUNTRY")
                || type.contains("REGION")
                || type.contains("DISTRICT")
                || type.contains("STREET")
                || type.contains("HOUSE")
                || type.contains("ADDRESS")) {
            return NerEntity.LOCATION;
        }
        if (type.startsWith("ORG")
                || type.contains("ORGANIZATION")
                || type.contains("COMPANY")
                || type.contains("UNIVERSITY")) {
            return NerEntity.ORGANIZATION;
        }
        return null;
    }

    private List<int[]> chunks(String text) {
        if (text.length() <= MAX_CHARS_PER_CHUNK) {
            return List.of(new int[] {0, text.length()});
        }
        List<int[]> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + MAX_CHARS_PER_CHUNK);
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end);
                int space = text.lastIndexOf(' ', end);
                int cut = Math.max(newline, space);
                if (cut > start + MAX_CHARS_PER_CHUNK / 2) {
                    end = cut;
                }
            }
            chunks.add(new int[] {start, end});
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private List<NerEntity> merge(List<NerEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        List<NerEntity> sorted = new ArrayList<>(entities);
        sorted.sort(Comparator.comparingInt(NerEntity::start).thenComparingInt(NerEntity::end));
        List<NerEntity> merged = new ArrayList<>();
        NerEntity current = sorted.getFirst();
        for (int index = 1; index < sorted.size(); index++) {
            NerEntity next = sorted.get(index);
            if (current.type().equals(next.type()) && next.start() <= current.end() + 1) {
                current =
                        new NerEntity(
                                current.type(),
                                current.start(),
                                Math.max(current.end(), next.end()),
                                Math.max(current.confidence(), next.confidence()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private Map<Integer, String> loadLabels(Path configPath) throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readAllBytes(configPath));
        JsonNode id2label = root.get("id2label");
        if (id2label == null || !id2label.isObject()) {
            throw new IOException("NER config has no id2label");
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        id2label.fields()
                .forEachRemaining(
                        entry ->
                                result.put(
                                        Integer.parseInt(entry.getKey()),
                                        entry.getValue().asText()));
        return result;
    }

    private String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class EntityBuilder {
        private final String type;
        private final int start;
        private int end;
        private double confidence;

        private EntityBuilder(String type, int start, int end, double confidence) {
            this.type = type;
            this.start = start;
            this.end = end;
            this.confidence = confidence;
        }
    }
}
