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
            log.warn("llm_not_configured api_key=absent");
            throw new LlmNotConfiguredException();
        }

        long startedAt = System.nanoTime();
        long deadline = startedAt + properties.getTotalTimeout().toNanos();
        List<String> models = new ArrayList<>();
        models.add(properties.getModel());
        models.addAll(properties.getFallbackModels());
        log.info(
                "llm_chain_started models={} prompt_chars={} json_schema={} total_timeout={}",
                models,
                request.userPrompt() == null ? 0 : request.userPrompt().length(),
                request.jsonSchema() != null,
                properties.getTotalTimeout());

        for (String model : models) {
            LlmResponse response = tryModel(model, request, deadline);
            if (response != null) {
                log.info(
                        "llm_chain_succeeded model={} elapsed_ms={} attempts_budget={}",
                        model,
                        elapsedMs(startedAt),
                        properties.getMaxAttempts());
                return response;
            }
        }
        log.error(
                "llm_chain_exhausted models={} elapsed_ms={} error={}",
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
                        "llm_total_timeout model={} attempt={} format={}",
                        model,
                        attempt + 1,
                        format);
                throw new LlmUnavailableException("LLM total timeout has expired", null);
            }
            attempt++;
            long attemptStartedAt = System.nanoTime();
            log.info(
                    "llm_request_sent model={} attempt={} format={} prompt_chars={}",
                    model,
                    attempt,
                    format,
                    request.userPrompt() == null ? 0 : request.userPrompt().length());
            try {
                ChatResponse response = callOnce(model, request, format, deadline);
                LlmResponse mapped = toResponse(response, model);
                log.info(
                        "llm_response_received model={} attempt={} format={} elapsed_ms={}"
                                + " content_chars={} prompt_tokens={} completion_tokens={}"
                                + " finish_reason={}",
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
                        "llm_auth_rejected model={} attempt={} elapsed_ms={} error={}",
                        model,
                        attempt,
                        elapsedMs(attemptStartedAt),
                        LogSupport.describe(e));
                throw new LlmAuthException("LLM rejected the API key", e);
            } catch (BadRequestException e) {
                format = degrade(format);
                if (format == null) {
                    log.warn(
                            "llm_request_failed model={} attempt={} elapsed_ms={} status=bad_request"
                                    + " error={}",
                            model,
                            attempt,
                            elapsedMs(attemptStartedAt),
                            LogSupport.describe(e));
                    return null;
                }
                log.warn(
                        "llm_format_degraded model={} attempt={} next_format={} error={}",
                        model,
                        attempt,
                        format,
                        LogSupport.describe(e));
            } catch (NotFoundException e) {
                log.warn(
                        "llm_model_not_found model={} attempt={} error={}",
                        model,
                        attempt,
                        LogSupport.describe(e));
                return null;
            } catch (RateLimitException e) {
                log.warn(
                        "llm_rate_limited model={} attempt={} elapsed_ms={} error={}",
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
                            "llm_quota_exhausted model={} attempt={} http_status={}",
                            model,
                            attempt,
                            e.statusCode());
                    throw new LlmQuotaException("LLM quota or credits are exhausted", e);
                }
                if (e.statusCode() == 401 || e.statusCode() == 403) {
                    log.warn(
                            "llm_auth_rejected model={} attempt={} http_status={}",
                            model,
                            attempt,
                            e.statusCode());
                    throw new LlmAuthException("LLM rejected the API key", e);
                }
                if (e.statusCode() >= 500) {
                    log.warn(
                            "llm_request_failed model={} attempt={} http_status={} elapsed_ms={}"
                                    + " error={}",
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
                            "llm_request_failed model={} attempt={} http_status={} error={}",
                            e.statusCode(),
                            model,
                            attempt,
                            LogSupport.describe(e));
                    return null;
                }
            } catch (OpenAIIoException e) {
                log.warn(
                        "llm_transport_error model={} attempt={} elapsed_ms={} error={}",
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
                            "llm_timeout model={} attempt={} elapsed_ms={} read_timeout={} error={}",
                            model,
                            attempt,
                            elapsedMs(attemptStartedAt),
                            properties.getReadTimeout(),
                            LogSupport.describe(e));
                    return null;
                }
                log.warn(
                        "llm_response_unreadable model={} attempt={} elapsed_ms={} error={}",
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
                        "llm_request_failed model={} attempt={} elapsed_ms={} error={}",
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
                        .maxTokens(properties.getMaxTokens())
                        // Spring AI по умолчанию ставит 60 с и перекрывает таймауты SDK:
                        // без этого read-timeout/total-timeout из конфигурации не работают.
                        .timeout(callTimeout);

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
                    "llm_model_exhausted model={} attempts={} max_attempts={} error={}",
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
                    "llm_retry_skipped model={} attempt={} delay_ms={} reason=total_timeout",
                    model,
                    attempt,
                    delayMillis);
            return false;
        }
        log.info(
                "llm_retry_scheduled model={} attempt={} delay_ms={} error={}",
                model,
                attempt,
                delayMillis,
                LogSupport.describe(cause));
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("llm_retry_interrupted model={} attempt={}", model, attempt);
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
