package ats.vacancy.llm;

import ats.vacancy.config.VacancyLlmProperties;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import com.openai.errors.BadRequestException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * OpenAI-совместимый клиент OpenRouter через Spring AI. Повторы, деградация response_format и
 * переключение моделей реализованы здесь, а не в конфигурации клиента.
 */
@Slf4j
@Component
public class SpringAiLlmClient implements LlmClient {

    private static final String ONLY_JSON_INSTRUCTION =
            "Верни только один JSON-объект по схеме, без текста вокруг.";

    private final VacancyLlmProperties properties;

    public SpringAiLlmClient(VacancyLlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LlmNotConfiguredException();
        }

        long deadline = System.nanoTime() + properties.getTotalTimeout().toNanos();
        List<String> models = new ArrayList<>();
        models.add(properties.getModel());
        models.addAll(properties.getFallbackModels());

        for (String model : models) {
            LlmResponse response = tryModel(model, request, deadline);
            if (response != null) {
                return response;
            }
        }
        throw new LlmUnavailableException(
                "All LLM models are unavailable or the total timeout has expired", null);
    }

    /** Возвращает null, если модель исчерпана и нужно перейти к следующей. */
    private LlmResponse tryModel(String model, LlmRequest request, long deadline) {
        FormatMode format = request.jsonSchema() == null ? FormatMode.NONE : FormatMode.JSON_SCHEMA;
        int attempt = 0;

        while (true) {
            ensureTimeLeft(deadline);
            attempt++;
            try {
                ChatResponse response = callOnce(model, request, format, deadline);
                return toResponse(response, model);
            } catch (UnauthorizedException | PermissionDeniedException e) {
                throw new LlmAuthException("LLM rejected the API key", e);
            } catch (BadRequestException e) {
                format = degrade(format);
                if (format == null) {
                    log.warn("LLM model {} rejected the request, trying next model", model);
                    return null;
                }
                log.debug("LLM model {} rejected response_format, degrading to {}", model, format);
            } catch (NotFoundException e) {
                log.warn("LLM model {} not found, trying next model", model);
                return null;
            } catch (RateLimitException e) {
                if (!sleepBeforeRetry(model, attempt, deadline, e)) {
                    return null;
                }
            } catch (OpenAIServiceException e) {
                if (e.statusCode() == 402) {
                    throw new LlmQuotaException("LLM quota or credits are exhausted", e);
                }
                if (e.statusCode() == 401 || e.statusCode() == 403) {
                    throw new LlmAuthException("LLM rejected the API key", e);
                }
                if (e.statusCode() >= 500) {
                    if (!sleepBeforeRetry(model, attempt, deadline, e)) {
                        return null;
                    }
                } else {
                    log.warn(
                            "LLM model {} returned HTTP {}, trying next model",
                            model,
                            e.statusCode());
                    return null;
                }
            } catch (OpenAIIoException e) {
                if (!sleepBeforeRetry(model, attempt, deadline, e)) {
                    return null;
                }
            }
        }
    }

    private ChatResponse callOnce(
            String model, LlmRequest request, FormatMode format, long deadline) {
        Duration remaining = Duration.ofNanos(Math.max(1, deadline - System.nanoTime()));
        Duration callTimeout =
                remaining.compareTo(properties.getReadTimeout()) < 0
                        ? remaining
                        : properties.getReadTimeout();

        OpenAIClient client =
                OpenAIOkHttpClient.builder()
                        .apiKey(properties.getApiKey())
                        .baseUrl(properties.getBaseUrl())
                        .timeout(
                                Timeout.builder()
                                        .connect(
                                                properties
                                                                        .getConnectTimeout()
                                                                        .compareTo(callTimeout)
                                                                < 0
                                                        ? properties.getConnectTimeout()
                                                        : callTimeout)
                                        .read(callTimeout)
                                        .request(callTimeout)
                                        .build())
                        .maxRetries(0)
                        .build();

        OpenAiChatOptions.Builder options =
                OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens());

        if (format != FormatMode.NONE) {
            var responseFormat =
                    OpenAiChatModel.ResponseFormat.builder()
                            .type(
                                    format == FormatMode.JSON_SCHEMA
                                            ? OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA
                                            : OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                            .strict(format == FormatMode.JSON_SCHEMA);
            if (format == FormatMode.JSON_SCHEMA) {
                responseFormat.jsonSchema(request.jsonSchema());
            }
            options.responseFormat(responseFormat.build());
        }

        String userPrompt =
                format == FormatMode.NONE
                        ? request.userPrompt() + "\n\n" + ONLY_JSON_INSTRUCTION
                        : request.userPrompt();

        OpenAiChatModel chatModel =
                OpenAiChatModel.builder()
                        .openAiClient(client)
                        .openAiClientAsync(client.async())
                        .build();
        return chatModel.call(
                new Prompt(
                        List.of(
                                new SystemMessage(request.systemPrompt()),
                                new UserMessage(userPrompt)),
                        options.build()));
    }

    private LlmResponse toResponse(ChatResponse response, String requestedModel) {
        if (response == null || response.getResult() == null) {
            throw new LlmInvalidResponseException("LLM returned an empty response", null);
        }
        String content = response.getResult().getOutput().getText();
        String finishReason =
                response.getResult().getMetadata() == null
                        ? null
                        : response.getResult().getMetadata().getFinishReason();

        if ((content == null || content.isBlank()) && "length".equalsIgnoreCase(finishReason)) {
            throw new LlmTruncatedException("LLM response was truncated by max-tokens");
        }

        String actualModel =
                response.getMetadata() == null ? null : response.getMetadata().getModel();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return new LlmResponse(
                content == null ? "" : content,
                actualModel == null || actualModel.isBlank() ? requestedModel : actualModel,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                finishReason);
    }

    /** null означает, что деградировать больше некуда. */
    private static FormatMode degrade(FormatMode current) {
        return switch (current) {
            case JSON_SCHEMA -> FormatMode.JSON_OBJECT;
            case JSON_OBJECT -> FormatMode.NONE;
            case NONE -> null;
        };
    }

    private boolean sleepBeforeRetry(String model, int attempt, long deadline, Exception cause) {
        if (attempt >= properties.getMaxAttempts()) {
            log.warn(
                    "LLM model {} exhausted {} attempts: {}",
                    model,
                    attempt,
                    cause.getClass().getSimpleName());
            return false;
        }
        long delayMillis =
                (long)
                        (properties.getDelay().toMillis()
                                * Math.pow(properties.getMultiplier(), attempt - 1));
        if (System.nanoTime() + Duration.ofMillis(delayMillis).toNanos() >= deadline) {
            return false;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("LLM call was interrupted", e);
        }
        return true;
    }

    private static void ensureTimeLeft(long deadline) {
        if (System.nanoTime() >= deadline) {
            throw new LlmUnavailableException("LLM total timeout has expired", null);
        }
    }

    private enum FormatMode {
        JSON_SCHEMA,
        JSON_OBJECT,
        NONE
    }
}
