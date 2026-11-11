package ats.support.logging;

import java.util.UUID;
import org.slf4j.MDC;

/** Единое имя корреляционного идентификатора для HTTP, gRPC, LLM и фоновых задач. */
public final class RequestIds {

    public static final String MDC_KEY = "request_id";
    public static final String HTTP_HEADER = "X-Request-Id";
    public static final String GRPC_HEADER = "x-request-id";
    public static final String RESPONSE_HEADER = "X-Request-Id";

    private RequestIds() {}

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrGenerate() {
        String current = current();
        return current == null || current.isBlank() ? generate() : current;
    }

    public static String generate() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static void put(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    public static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 64) {
            return generate();
        }
        return candidate.strip();
    }
}
