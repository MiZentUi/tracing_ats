package ats.resume.infrastructure.notification;

import ats.resume.config.TelegramProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Telegram Bot API. Текст сообщения и токен не логируются. */
@Slf4j
@Component
public class TelegramNotificationSender implements NotificationSender {

    private final TelegramProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public TelegramNotificationSender(TelegramProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    }

    @Override
    public void send(String text) {
        if (!properties.isConfigured()) {
            throw new NotificationException(false, "telegram is not configured");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", properties.getChatId());
        body.put("text", text);
        body.put("disable_web_page_preview", true);
        long startedAt = System.nanoTime();
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            properties.getBaseUrl()
                                                    + "/bot"
                                                    + properties.getBotToken()
                                                    + "/sendMessage"))
                            .timeout(properties.getTimeout())
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            mapper.writeValueAsString(body)))
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info(
                        "telegram_notification_sent status={} elapsed_ms={}",
                        response.statusCode(),
                        (System.nanoTime() - startedAt) / 1_000_000);
                return;
            }
            boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
            log.warn(
                    "telegram_notification_failed status={} elapsed_ms={}",
                    response.statusCode(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw new NotificationException(
                    retryable, "telegram returned status " + response.statusCode());
        } catch (IOException e) {
            log.warn(
                    "telegram_notification_failed error={} elapsed_ms={}",
                    e.getClass().getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw new NotificationException(true, "telegram request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotificationException(false, "telegram request interrupted", e);
        }
    }
}
