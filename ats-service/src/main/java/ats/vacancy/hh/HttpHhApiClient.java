package ats.vacancy.hh;

import ats.resume.config.HhProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * HTTP-клиент hh.ru. Тела запросов и токен не логируются. POST не повторяем автоматически: повтор
 * создания черновика или подписки мог бы создать дубль.
 */
@Slf4j
@Component
public class HttpHhApiClient implements HhApiClient {

    private final HhProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public HttpHhApiClient(HhProperties properties) {
        this.properties = properties;
        this.mapper = new ObjectMapper();
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(properties.getTimeout())
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @Override
    public String createDraft(HhVacancyDraft draft) {
        JsonNode response = send("POST", "/vacancies/drafts", draftBody(draft), false);
        String id = response.path("id").asText("");
        if (id.isBlank()) {
            throw new HhApiException(
                    "HH_DRAFT_ID_MISSING",
                    HttpStatus.BAD_GATEWAY,
                    false,
                    "hh.ru did not return a draft id");
        }
        return id;
    }

    @Override
    public void updateDraft(String draftId, HhVacancyDraft draft) {
        send("PUT", "/vacancies/drafts/" + encode(draftId), draftBody(draft), false);
    }

    private ObjectNode draftBody(HhVacancyDraft draft) {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", draft.name());
        body.put("description", draft.description());
        ObjectNode area = body.putObject("area");
        area.put("id", properties.getDefaultArea());
        body.putObject("experience").put("id", properties.getDefaultExperience());
        body.putObject("employment_form").put("id", properties.getDefaultEmploymentForm());
        body.putObject("schedule").put("id", properties.getDefaultSchedule());
        body.putArray("professional_roles")
                .addObject()
                .put("id", properties.getDefaultProfessionalRole());
        return body;
    }

    @Override
    public List<String> publishDraft(String draftId) {
        JsonNode response =
                send("POST", "/vacancies/drafts/" + encode(draftId) + "/publish", null, false);
        JsonNode ids = response.path("vacancy_ids");
        List<String> result = new ArrayList<>();
        if (ids.isArray()) {
            ids.forEach(node -> result.add(node.asText()));
        }
        if (result.isEmpty()) {
            throw new HhApiException(
                    "HH_PUBLISH_EMPTY",
                    HttpStatus.BAD_GATEWAY,
                    false,
                    "hh.ru did not return a published vacancy id");
        }
        return result;
    }

    @Override
    public HhVacancy getVacancy(String vacancyId) {
        JsonNode response = send("GET", "/vacancies/" + encode(vacancyId), null, true);
        return new HhVacancy(
                response.path("id").asText(vacancyId),
                response.path("name").asText(""),
                response.path("alternate_url").asText(null),
                response.path("employer").path("id").asText(null),
                response.path("archived").asBoolean(false));
    }

    @Override
    public String createSubscription(String url, String employerId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("url", url);
        ObjectNode action = body.putArray("actions").addObject();
        action.put("type", "NEW_NEGOTIATION_VACANCY");
        action.putObject("settings").put("vacancies_only_mine", true);
        JsonNode response = send("POST", "/webhook/subscriptions", body, false);
        String id = response.path("id").asText("");
        if (id.isBlank()) {
            throw new HhApiException(
                    "HH_SUBSCRIPTION_ID_MISSING",
                    HttpStatus.BAD_GATEWAY,
                    false,
                    "hh.ru did not return a subscription id");
        }
        return id;
    }

    @Override
    public java.util.List<HhSubscription> listSubscriptions() {
        JsonNode response = send("GET", "/webhook/subscriptions", null, true);
        JsonNode items = response.path("items");
        java.util.List<HhSubscription> result = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(
                    item ->
                            result.add(
                                    new HhSubscription(
                                            item.path("id").asText(""),
                                            item.path("url").asText(""))));
        }
        return result;
    }

    @Override
    public void deleteSubscription(String subscriptionId) {
        send("DELETE", "/webhook/subscriptions/" + encode(subscriptionId), null, false);
    }

    @Override
    public void archiveVacancy(String vacancyId) {
        if (properties.getEmployerId() == null || properties.getEmployerId().isBlank()) {
            throw new HhApiException(
                    "HH_EMPLOYER_ID_REQUIRED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    false,
                    "employer id is required for archiving");
        }
        send(
                "PUT",
                "/employers/"
                        + encode(properties.getEmployerId())
                        + "/vacancies/archived/"
                        + encode(vacancyId),
                mapper.createObjectNode(),
                false);
    }

    private JsonNode send(String method, String path, JsonNode body, boolean retry) {
        int attempts = retry ? properties.getMaxAttempts() : 1;
        HhApiException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                HttpRequest.Builder builder =
                        HttpRequest.newBuilder(URI.create(properties.getApiBaseUrl() + path))
                                .timeout(properties.getTimeout())
                                .header("Accept", "application/json")
                                .header("Authorization", "Bearer " + properties.getToken())
                                .header("User-Agent", properties.getUserAgent())
                                .header("HH-User-Agent", properties.getUserAgent());
                if (body == null) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.header("Content-Type", "application/json")
                            .method(
                                    method,
                                    HttpRequest.BodyPublishers.ofString(
                                            mapper.writeValueAsString(body)));
                }
                HttpResponse<String> response =
                        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                log.info(
                        "outbound_request_sent target=hh method={} path={} status={}"
                                + " attempt={} elapsed_ms={}",
                        method,
                        path,
                        response.statusCode(),
                        attempt,
                        (System.nanoTime() - startedAt) / 1_000_000);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parse(response.body());
                }
                last = toException(response);
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
                                "hh.ru request failed",
                                e);
                log.warn(
                        "outbound_request_failed target=hh method={} path={} attempt={}"
                                + " error={}",
                        method,
                        path,
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
                        "hh.ru request interrupted",
                        e);
            }
        }
        throw last == null
                ? new HhApiException(
                        "HH_UNAVAILABLE", HttpStatus.BAD_GATEWAY, false, "hh.ru request failed")
                : last;
    }

    private HhApiException toException(HttpResponse<String> response) {
        int code = response.statusCode();
        boolean retryable = code == 429 || code >= 500;
        String errorCode =
                switch (code) {
                    case 401 -> "HH_UNAUTHORIZED";
                    case 403 -> "HH_FORBIDDEN";
                    case 404 -> "HH_NOT_FOUND";
                    case 429 -> "HH_RATE_LIMITED";
                    default -> code >= 500 ? "HH_UNAVAILABLE" : "HH_BAD_REQUEST";
                };
        return new HhApiException(
                errorCode,
                HttpStatus.valueOf(code >= 400 && code < 600 ? code : 502),
                retryable,
                "hh.ru returned status " + code);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new HhApiException(
                    "HH_INVALID_RESPONSE",
                    HttpStatus.BAD_GATEWAY,
                    false,
                    "hh.ru returned invalid json",
                    e);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("hh retry interrupted", e);
        }
    }
}
