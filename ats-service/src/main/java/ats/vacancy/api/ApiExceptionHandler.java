package ats.vacancy.api;

import ats.extraction.domain.error.ExtractionException;
import ats.gateway.model.ApiStatusResponse;
import ats.vacancy.application.ApiException;
import ats.vacancy.dictionary.DictionaryRpcException;
import ats.vacancy.dictionary.DictionaryUnavailableException;
import ats.vacancy.llm.LlmException;
import ats.vacancy.support.LogSupport;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiStatusResponse> handleApi(ApiException exception) {
        log.info(
                "api_request_rejected status={} code={} detail={}",
                exception.status().value(),
                exception.code(),
                exception.getMessage());
        return response(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(DictionaryUnavailableException.class)
    public ResponseEntity<ApiStatusResponse> handleDictionaryUnavailable(
            DictionaryUnavailableException exception) {
        log.warn("dictionary_unavailable error={}", LogSupport.describe(exception));
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DICTIONARY_UNAVAILABLE",
                "Dictionary service is unavailable");
    }

    @ExceptionHandler(DictionaryRpcException.class)
    public ResponseEntity<ApiStatusResponse> handleDictionaryRpc(DictionaryRpcException exception) {
        HttpStatus status =
                switch (exception.code()) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case FAILED_PRECONDITION, ALREADY_EXISTS -> HttpStatus.CONFLICT;
                    case INVALID_ARGUMENT -> HttpStatus.UNPROCESSABLE_ENTITY;
                    default -> HttpStatus.BAD_GATEWAY;
                };
        log.info(
                "dictionary_request_rejected code={} detail={}",
                exception.code(),
                exception.getMessage());
        return response(status, "DICTIONARY_ERROR", exception.getMessage());
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ApiStatusResponse> handleLlm(LlmException exception) {
        log.warn(
                "llm_call_failed http_status={} code={} error={}",
                exception.httpStatus(),
                exception.errorCode(),
                LogSupport.describe(exception));
        return response(
                HttpStatus.valueOf(exception.httpStatus()),
                exception.errorCode(),
                exception.getMessage());
    }

    @ExceptionHandler(ExtractionException.class)
    public ResponseEntity<ApiStatusResponse> handleExtraction(ExtractionException exception) {
        log.warn(
                "text_extraction_failed code={} error={}",
                exception.errorCode(),
                LogSupport.describe(exception));
        return response(
                HttpStatus.valueOf(exception.errorCode().httpStatus()),
                exception.errorCode().name(),
                exception.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiStatusResponse> handleValidation(Exception exception) {
        log.warn("request_validation_failed error={}", LogSupport.describe(exception));
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Invalid request body");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiStatusResponse> handleUnexpected(Exception exception) {
        log.error("api_error_unexpected", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    private static ResponseEntity<ApiStatusResponse> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(
                        new ApiStatusResponse()
                                .code(status.value())
                                .status(code)
                                .message(message == null ? status.getReasonPhrase() : message));
    }
}
