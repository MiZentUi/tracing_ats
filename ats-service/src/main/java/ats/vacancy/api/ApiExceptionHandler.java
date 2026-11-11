package ats.vacancy.api;

import ats.extraction.domain.error.ExtractionException;
import ats.gateway.model.ApiStatusResponse;
import ats.vacancy.application.ApiException;
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
                "Request rejected: status={}, code={}, detail={}",
                exception.status().value(),
                exception.code(),
                exception.getMessage());
        return response(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ApiStatusResponse> handleLlm(LlmException exception) {
        log.warn(
                "LLM call failed: httpStatus={}, code={}, cause={}",
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
                "Text extraction failed: code={}, cause={}",
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
        log.warn("Request validation failed: cause={}", LogSupport.describe(exception));
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Invalid request body");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiStatusResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected API error", exception);
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
