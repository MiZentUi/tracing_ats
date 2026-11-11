package ats.vacancy.llm;

import ats.vacancy.config.VacancyLlmProperties;
import ats.vacancy.support.LogSupport;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import com.openai.errors.BadRequestException;
import com.openai.errors.NotFoundException;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
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
            log.warn("LLM is not configured: vacancy.llm.api-key is empty");
            throw new LlmNotConfiguredException();
        }

        long startedAt = System.nanoTime();
        long deadline = startedAt + properties.getTotalTimeout().toNanos();
        List<String> models = new ArrayList<>();
        models.add(properties.getModel());
        models.addAll(properties.getFallbackModels());
        log.info(
                "LLM chain started: models={}, promptChars={}, hasJsonSchema={}, totalTimeout={}",
                models,
                request.userPrompt() == null ? 0 : request.userPrompt().length(),
                request.jsonSchema() != null,
                properties.getTotalTimeout());

        for (String model : models) {
            LlmResponse response = tryModel(model, request, deadline);
            if (response != null) {
                log.info(
                        "LLM chain succeeded: model={}, elapsedMs={}, attemptsBudget={}",
                        model,
                        elapsedMs(startedAt),
                        properties.getMaxAttempts());
                return response;
            }
        }
        log.error(
                "LLM chain exhausted: models={}, elapsedMs={}, cause={}",
                models,
                elapsedMs(startedAt),
                "all models returned no usable response or the total timeout expired");
        throw new LlmUnavailableException(
                "All LLM models are unavailable or the total timeout has expired", null);
    }

    /** Возвращает null, если модель исчерпана и нужно перейти к следующей. */
    private LlmResponse tryModel(String model, LlmRequest request, long deadline) {
        FormatMode format = request.jsonSchema() == null ? FormatMode.NONE : FormatMode.JSON_SCHEMA;
        int attempt = 0;

        while (true) {
            if (System.nanoTime() >= deadline) {
                log.warn(
                        "LLM total timeout expired before attempt: model={}, attempt={}, format={}",
                        model,
                        attempt + 1,
                        format);
                throw new LlmUnavailableException("LLM total timeout has expired", null);
            }
            attempt++;
            long attemptStartedAt = System.nanoTime();
            log.info(
                    "LLM attempt started: model={}, attempt={}, format={}, promptChars={}",
                    model,
                    attempt,
                    format,
                    request.userPrompt() == null ? 0 : request.userPrompt().length());
            try {
                ChatResponse response = callOnce(model, request, format, deadline);
                LlmResponse mapped = toResponse(response, model);
                log.info(
                        "LLM attempt succeeded: model={}, attempt={}, format={}, elapsedMs={},"
                                + " contentChars={}, promptTokens={}, completionTokens={},"
                                + " finishReason={}",
                        model,
                        attempt,
                        format,
                        elapsedMs(attemptStartedAt),
                        mapped.content().length(),
                        mapped.promptTokens(),
                        mapped.completionTokens(),
                        mapped.finishReason());
                return mapped;
            } catch (UnauthorizedException | PermissionDeniedException e) {
                log.warn(
                        "LLM rejected the API key: model={}, attempt={}, elapsedMs={}, cause={}",
                        model,
                        attempt,
                        elapsedMs(attemptStartedAt),
                        LogSupport.describe(e));
                throw new LlmAuthException("LLM rejected the API key", e);
            } catch (BadRequestException e) {
                format = degrade(format);
                if (format == null) {
                    log.warn(
                            "LLM rejected the request in every format, trying next model:"
                                    + " model={}, attempt={}, elapsedMs={}, cause={}",
                            model,
                            attempt,
                            elapsedMs(attemptStartedAt),
                            LogSupport.describe(e));
                    return null;
                }
                log.warn(
                        "LLM rejected response format, degrading: model={}, attempt={},"
                                + " nextFormat={}, cause={}",
                        model,
                        attempt,
                        format,
                        LogSupport.describe(e));
            } catch (NotFoundException e) {
                log.warn(
                        "LLM model not found, trying next model: model={}, attempt={}, cause={}",
                        model,
                        attempt,
                        LogSupport.describe(e));
                return null;
            } catch (RateLimitException e) {
                log.warn(
                        "LLM rate limited: model={}, attempt={}, elapsedMs={}, cause={}",
                        model,
                        attempt,
                        elapsedMs(attemptStartedAt),
                        LogSupport.describe(e));
                if (!sleepBeforeRetry(model, attempt, deadline, e)) {
                    return null;
                }
            } catch (OpenAIServiceException e) {
                if (e.statusCode() == 402) {
                    log.warn(
                            "LLM quota exhausted: model={}, attempt={}, status={}",
                            model,
                            attempt,
                            e.statusCode());
                    throw new LlmQuotaException("LLM quota or credits are exhausted", e);
                }
                if (e.statusCode() == 401 || e.statusCode() == 403) {
                    log.warn(
                            "LLM rejected the API key: model={}, attempt={}, status={}",
                            model,
                            attempt,
                            e.statusCode());
                    throw new LlmAuthException("LLM rejected the API key", e);
                }
                if (e.statusCode() >= 500) {
                    log.warn(
                            "LLM server error: model={}, attempt={}, status={}, elapsedMs={},"
                                    + " cause={}",
                            model,
                            attempt,
                            e.statusCode(),
                            elapsedMs(attemptStartedAt),
                            LogSupport.describe(e));
                    if (!sleepBeforeRetry(model, attempt, deadline, e)) {
                        return null;
                    }
                } else {
                    log.warn(
                            "LLM returned HTTP {}, trying next model: model={}, attempt={},"
                                    + " cause={}",
                            e.statusCode(),
                            model,
                            attempt,
                            LogSupport.describe(e));
                    return null;
                }
            } catch (OpenAIIoException e) {
                log.warn(
                        "LLM transport error: model={}, attempt={}, elapsedMs={}, cause={}",
                        model,
                        attempt,
                        elapsedMs(attemptStartedAt),
                        LogSupport.describe(e));
                if (!sleepBeforeRetry(model, attempt, deadline, e)) {
                    return null;
                }
            } catch (OpenAIInvalidDataException e) {
                // Ответ оборвался на середине чтения: таймаут чтения или сброс HTTP/2.
                // Повторять ту же медленную модель бессмысленно, сразу идём к следующей.
                if (isTimeout(e)) {
                    log.warn(
                            "LLM timed out mid-response, trying next model: model={},"
                                    + " attempt={}, elapsedMs={}, readTimeout={}, cause={}",
                            model,
                            attempt,
                            elapsedMs(attemptStartedAt),
                            properties.getReadTimeout(),
                            LogSupport.describe(e));
                    return null;
                }
                log.warn(
                        "LLM response cannot be read: model={}, attempt={}, elapsedMs={},"
                                + " cause={}",
                        model,
                        attempt,
                        elapsedMs(attemptStartedAt),
                        LogSupport.describe(e));
                if (!sleepBeforeRetry(model, attempt, deadline, e)) {
                    return null;
                }
            } catch (OpenAIException e) {
                // Прочие ошибки SDK не должны вылетать наружу: по RFC-002 истёкшая
                // цепочка — это LlmUnavailableException, а не 500 для клиента.
                log.warn(
                        "LLM SDK error: model={}, attempt={}, elapsedMs={}, cause={}",
                        model,
                        attempt,
                        elapsedMs(attemptStartedAt),
                        LogSupport.describe(e));
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

    private static boolean isTimeout(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private boolean sleepBeforeRetry(String model, int attempt, long deadline, Exception cause) {
        if (attempt >= properties.getMaxAttempts()) {
            log.warn(
                    "LLM model exhausted: model={}, attempts={}, maxAttempts={}, cause={}",
                    model,
                    attempt,
                    properties.getMaxAttempts(),
                    LogSupport.describe(cause));
            return false;
        }
        long delayMillis =
                (long)
                        (properties.getDelay().toMillis()
                                * Math.pow(properties.getMultiplier(), attempt - 1));
        if (System.nanoTime() + Duration.ofMillis(delayMillis).toNanos() >= deadline) {
            log.warn(
                    "LLM retry skipped, total timeout would expire: model={}, attempt={},"
                            + " delayMs={}",
                    model,
                    attempt,
                    delayMillis);
            return false;
        }
        log.info(
                "LLM retry scheduled: model={}, attempt={}, delayMs={}, cause={}",
                model,
                attempt,
                delayMillis,
                LogSupport.describe(cause));
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("LLM retry was interrupted: model={}, attempt={}", model, attempt);
            throw new LlmUnavailableException("LLM call was interrupted", e);
        }
        return true;
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private enum FormatMode {
        JSON_SCHEMA,
        JSON_OBJECT,
        NONE
    }
}
