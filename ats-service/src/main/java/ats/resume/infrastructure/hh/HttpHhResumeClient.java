package ats.resume.infrastructure.hh;

import ats.resume.config.HhProperties;
import ats.vacancy.hh.HhApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Забирает резюме по id. Текст резюме и контакты не логируются. 403 — ожидаемый случай: у
 * работодателя может не быть платного доступа к резюме.
 */
@Slf4j
@Component
public class HttpHhResumeClient implements HhResumeClient {

    private final HhProperties properties;
    private final HhResumeRenderer renderer;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public HttpHhResumeClient(HhProperties properties, HhResumeRenderer renderer) {
        this.properties = properties;
        this.renderer = renderer;
        this.mapper = new ObjectMapper();
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(properties.getTimeout())
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @Override
    public HhResume fetch(String resumeId, String topicId) {
        String path = "/resumes/" + encode(resumeId);
        if (topicId != null && !topicId.isBlank()) {
            path = path + "?topic_id=" + encode(topicId);
        }
        JsonNode response = get(path);
        return renderer.render(resumeId, response);
    }

    private JsonNode get(String path) {
        int attempts = properties.getMaxAttempts();
        HhApiException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                HttpRequest request =
                        HttpRequest.newBuilder(URI.create(properties.getApiBaseUrl() + path))
                                .timeout(properties.getTimeout())
                                .header("Accept", "application/json")
                                .header("Authorization", "Bearer " + properties.getToken())
                                .header("User-Agent", properties.getUserAgent())
                                .header("HH-User-Agent", properties.getUserAgent())
                                .GET()
                                .build();
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info(
                        "outbound_request_sent target=hh_resume method=GET status={}"
                                + " attempt={} elapsed_ms={}",
                        response.statusCode(),
                        attempt,
                        (System.nanoTime() - startedAt) / 1_000_000);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return mapper.readTree(response.body());
                }
                last = toException(response.statusCode());
                if (!last.retryable() || attempt == attempts) {
                    throw last;
                }
                sleep(properties.getRetryDelay());
            } catch (IOException e) {
                last =
                        new HhApiException(
                                "HH_UNAVAILABLE",
                                HttpStatus.BAD_GATEWAY,
                                true,
                                "hh.ru resume request failed",
                                e);
                log.warn(
                        "outbound_request_failed target=hh_resume attempt={} error={}",
                        attempt,
                        e.getClass().getSimpleName());
                if (attempt == attempts) {
                    throw last;
                }
                sleep(properties.getRetryDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HhApiException(
                        "HH_INTERRUPTED",
                        HttpStatus.BAD_GATEWAY,
                        false,
                        "hh.ru resume request interrupted",
                        e);
            }
        }
        throw last == null
                ? new HhApiException(
                        "HH_UNAVAILABLE", HttpStatus.BAD_GATEWAY, false, "hh.ru request failed")
                : last;
    }

    private HhApiException toException(int code) {
        String errorCode =
                switch (code) {
                    case 401 -> "HH_UNAUTHORIZED";
                    case 403 -> "HH_RESUME_FORBIDDEN";
                    case 404 -> "HH_RESUME_NOT_FOUND";
                    case 429 -> "HH_RESUME_RATE_LIMITED";
                    default -> code >= 500 ? "HH_UNAVAILABLE" : "HH_BAD_REQUEST";
                };
        return new HhApiException(
                errorCode,
                HttpStatus.valueOf(code >= 400 && code < 600 ? code : 502),
                code == 429 || code >= 500,
                "hh.ru resume request returned status " + code);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("hh resume retry interrupted", e);
        }
    }
}
