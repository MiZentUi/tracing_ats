package ats.vacancy.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ats.vacancy.config.VacancyLlmProperties;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SpringAiLlmClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final List<AutoCloseable> closeables = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable closeable : closeables) {
            closeable.close();
        }
    }

    private VacancyLlmProperties properties(String baseUrl) {
        VacancyLlmProperties properties = new VacancyLlmProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl(baseUrl);
        properties.setModel("primary/model");
        properties.setFallbackModels(List.of("fallback/model"));
        properties.setMaxAttempts(2);
        properties.setDelay(Duration.ofMillis(10));
        properties.setTotalTimeout(Duration.ofSeconds(10));
        properties.setReadTimeout(Duration.ofSeconds(5));
        properties.setConnectTimeout(Duration.ofSeconds(1));
        return properties;
    }

    private static LlmRequest request() {
        return new LlmRequest(
                "system", "<vacancy_text>\nJava\n</vacancy_text>", "{\"type\":\"object\"}");
    }

    @Test
    void failsFastWithoutKey() {
        SpringAiLlmClient client = new SpringAiLlmClient(properties("http://127.0.0.1:1"));
        SpringAiLlmClient noKey =
                new SpringAiLlmClient(
                        new VacancyLlmProperties() {
                            {
                                setApiKey("");
                            }
                        });
        assertThatThrownBy(() -> noKey.complete(request()))
                .isInstanceOf(LlmNotConfiguredException.class);
        // Валидный клиент здесь не вызывается; проверяем, что ключ проверяется до сети.
        assertThat(client).isNotNull();
    }

    @Test
    void retriesRateLimitThenSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String baseUrl =
                server(
                        body ->
                                calls.incrementAndGet() == 1
                                        ? new MockResponse(429, error("rate_limit"))
                                        : new MockResponse(200, success("ответ")));
        SpringAiLlmClient client = new SpringAiLlmClient(properties(baseUrl));

        LlmResponse response = client.complete(request());

        assertThat(response.content()).isEqualTo("ответ");
        assertThat(response.model()).isEqualTo("mock/mock-model");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void stopsOnUnauthorizedAndQuota() throws Exception {
        String unauthorized = server(body -> new MockResponse(401, error("invalid_api_key")));
        assertThatThrownBy(
                        () -> new SpringAiLlmClient(properties(unauthorized)).complete(request()))
                .isInstanceOf(LlmAuthException.class);

        String quota = server(body -> new MockResponse(402, error("quota")));
        assertThatThrownBy(() -> new SpringAiLlmClient(properties(quota)).complete(request()))
                .isInstanceOf(LlmQuotaException.class);
    }

    @Test
    void switchesToFallbackModel() throws Exception {
        List<String> models = new CopyOnWriteArrayList<>();
        String baseUrl =
                server(
                        body -> {
                            try {
                                String model = MAPPER.readTree(body).path("model").asText();
                                models.add(model);
                                if (model.equals("primary/model")) {
                                    return new MockResponse(429, error("busy"));
                                }
                                return new MockResponse(200, success("fallback"));
                            } catch (Exception e) {
                                return new MockResponse(500, error("broken"));
                            }
                        });
        VacancyLlmProperties properties = properties(baseUrl);
        properties.setMaxAttempts(1);
        LlmResponse response = new SpringAiLlmClient(properties).complete(request());

        assertThat(response.content()).isEqualTo("fallback");
        assertThat(models).containsExactly("primary/model", "fallback/model");
    }

    @Test
    void degradesResponseFormatAfterBadRequest() throws Exception {
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        String baseUrl =
                server(
                        body -> {
                            try {
                                requests.add(MAPPER.readTree(body));
                            } catch (Exception ignored) {
                                // ignore
                            }
                            return calls.incrementAndGet() < 3
                                    ? new MockResponse(400, error("unsupported response_format"))
                                    : new MockResponse(200, success("ok"));
                        });
        SpringAiLlmClient client = new SpringAiLlmClient(properties(baseUrl));

        assertThat(client.complete(request()).content()).isEqualTo("ok");
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).path("response_format").path("type").asText())
                .isEqualTo("json_schema");
        assertThat(requests.get(1).path("response_format").path("type").asText())
                .isEqualTo("json_object");
        assertThat(requests.get(2).has("response_format")).isFalse();
    }

    @Test
    void reportsTruncatedResponse() throws Exception {
        String baseUrl = server(body -> new MockResponse(200, completion("", "length")));
        assertThatThrownBy(() -> new SpringAiLlmClient(properties(baseUrl)).complete(request()))
                .isInstanceOf(LlmTruncatedException.class);
    }

    @Test
    void logsRequestWithoutPromptContent() throws Exception {
        String baseUrl = server(body -> new MockResponse(200, success("ok")));
        Logger logger = (Logger) LoggerFactory.getLogger(SpringAiLlmClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new SpringAiLlmClient(properties(baseUrl))
                    .complete(
                            new LlmRequest(
                                    "system", "СЕКРЕТНЫЙ ТЕКСТ РЕЗЮМЕ <vacancy_text>", null));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.startsWith("llm_request_sent"));
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(m -> m.contains("СЕКРЕТНЫЙ ТЕКСТ"));
    }

    @Test
    void switchesToCloudflareWhenPrimaryIsUnavailable() throws Exception {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        String primary =
                server(
                        body -> {
                            primaryCalls.incrementAndGet();
                            return new MockResponse(500, error("down"));
                        });
        String fallback =
                server(
                        body -> {
                            fallbackCalls.incrementAndGet();
                            return new MockResponse(200, success("cloudflare"));
                        });
        VacancyLlmProperties properties = properties(primary);
        properties.setMaxAttempts(1);
        properties.setFallbackModels(List.of());
        enableFallback(properties, fallback);

        LlmResponse response = new SpringAiLlmClient(properties).complete(request());

        assertThat(response.content()).isEqualTo("cloudflare");
        assertThat(primaryCalls).hasValue(1);
        assertThat(fallbackCalls).hasValue(1);
    }

    @Test
    void switchesToCloudflareOnPrimaryAuthRejection() throws Exception {
        String primary = server(body -> new MockResponse(401, error("invalid_api_key")));
        String fallback = server(body -> new MockResponse(200, success("cloudflare")));
        VacancyLlmProperties properties = properties(primary);
        properties.setMaxAttempts(1);
        properties.setFallbackModels(List.of());
        enableFallback(properties, fallback);

        LlmResponse response = new SpringAiLlmClient(properties).complete(request());

        assertThat(response.content()).isEqualTo("cloudflare");
    }

    private static void enableFallback(VacancyLlmProperties properties, String baseUrl) {
        properties.getFallback().setEnabled(true);
        properties.getFallback().setBaseUrl(baseUrl);
        properties.getFallback().setApiKey("cloudflare-key");
        properties.getFallback().setModel("@cf/qwen/qwen3.8-27b");
        properties.getFallback().setFallbackModels(List.of());
    }

    @Test
    void reportsUnavailableWhenResponseCannotBeRead() throws Exception {
        // Оборванный ответ раньше вылетал OpenAIInvalidDataException мимо клиента и давал 500.
        String baseUrl = server(body -> new MockResponse(200, "{\"choices\": ["));
        assertThatThrownBy(() -> new SpringAiLlmClient(properties(baseUrl)).complete(request()))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void switchesToFallbackWhenPrimaryAnswerIsUnreadable() throws Exception {
        List<String> models = new CopyOnWriteArrayList<>();
        String baseUrl =
                server(
                        body -> {
                            try {
                                String model = MAPPER.readTree(body).path("model").asText();
                                models.add(model);
                                if (model.equals("primary/model")) {
                                    return new MockResponse(200, "not-json");
                                }
                                return new MockResponse(200, success("fallback"));
                            } catch (Exception e) {
                                return new MockResponse(500, error("broken"));
                            }
                        });
        VacancyLlmProperties properties = properties(baseUrl);
        properties.setMaxAttempts(1);

        LlmResponse response = new SpringAiLlmClient(properties).complete(request());

        assertThat(response.content()).isEqualTo("fallback");
        assertThat(models).containsExactly("primary/model", "fallback/model");
    }

    @Test
    void switchesToFallbackWhenPrimaryTimesOut() throws Exception {
        List<String> models = new CopyOnWriteArrayList<>();
        String baseUrl =
                server(
                        body -> {
                            try {
                                String model = MAPPER.readTree(body).path("model").asText();
                                models.add(model);
                                if (model.equals("primary/model")) {
                                    Thread.sleep(1_500);
                                }
                                return new MockResponse(200, success("ok"));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return new MockResponse(500, error("interrupted"));
                            } catch (Exception e) {
                                return new MockResponse(500, error("broken"));
                            }
                        });
        VacancyLlmProperties properties = properties(baseUrl);
        properties.setMaxAttempts(1);
        properties.setReadTimeout(Duration.ofMillis(300));

        LlmResponse response = new SpringAiLlmClient(properties).complete(request());

        assertThat(response.content()).isEqualTo("ok");
        assertThat(models).containsExactly("primary/model", "fallback/model");
    }

    private String server(Function<String, MockResponse> handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Виртуальные потоки: тест с таймаутом отправляет запрос к запасной модели,
        // пока обработчик основной ещё спит.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/v1/chat/completions", exchange -> handle(exchange, handler));
        server.start();
        closeables.add(() -> server.stop(0));
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    private static void handle(HttpExchange exchange, Function<String, MockResponse> handler)
            throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        MockResponse response = handler.apply(body);
        byte[] encoded = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), encoded.length);
        exchange.getResponseBody().write(encoded);
        exchange.close();
    }

    private static String success(String content) {
        return completion(content, "stop");
    }

    private static String completion(String content, String finishReason) {
        try {
            return MAPPER.writeValueAsString(
                    java.util.Map.of(
                            "id",
                            "chatcmpl-mock",
                            "object",
                            "chat.completion",
                            "created",
                            1,
                            "model",
                            "mock/mock-model",
                            "choices",
                            List.of(
                                    java.util.Map.of(
                                            "index",
                                            0,
                                            "message",
                                            java.util.Map.of(
                                                    "role", "assistant", "content", content),
                                            "finish_reason",
                                            finishReason)),
                            "usage",
                            java.util.Map.of(
                                    "prompt_tokens", 1,
                                    "completion_tokens", 1,
                                    "total_tokens", 2)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String error(String message) {
        try {
            return MAPPER.writeValueAsString(
                    java.util.Map.of("error", java.util.Map.of("message", message)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record MockResponse(int status, String body) {}
}
