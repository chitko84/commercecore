package com.commercecore.common.web;

import com.commercecore.common.exception.NotFoundException;
import com.commercecore.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

/**
 * Centralized API error handling. Every error the API returns goes
 * through here (or through {@link ResponseEntityExceptionHandler}'s
 * hooks, which this class overrides) and comes out as an
 * {@link ApiError} -- never a raw stack trace or framework default
 * error page.
 *
 * Covers, per the Phase-1 requirement: validation errors, malformed
 * requests, not-found style application exceptions, and unexpected
 * server errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- Validation errors (e.g. @Valid @RequestBody failures) ---
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ApiError body = build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed.", request, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // --- Malformed request body (unreadable/invalid JSON, wrong type, etc.) ---
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        ApiError body = build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body could not be read.", request, null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // --- Constraint violations on path variables / request params ---
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                                HttpServletRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        ApiError body = build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed.", request, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // --- Not-found style application exceptions ---
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        ApiError body = build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        ApiError body = build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, null);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    // --- Everything else: unexpected server errors ---
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Log full details server-side, including the trace id for correlation.
        log.error("Unhandled exception [traceId={}] on {} {}",
                MDC.get(CorrelationIdFilter.MDC_KEY), request.getMethod(), request.getRequestURI(), ex);
        ApiError body = build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", request, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ApiError.FieldError toFieldError(FieldError fe) {
        return new ApiError.FieldError(fe.getField(), fe.getDefaultMessage());
    }

    private ApiError build(HttpStatus status, String code, String message,
                            WebRequest request, List<ApiError.FieldError> fieldErrors) {
        String path = request instanceof org.springframework.web.context.request.ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : "";
        return newApiError(status, code, message, path, fieldErrors);
    }

    private ApiError build(HttpStatus status, String code, String message,
                            HttpServletRequest request, List<ApiError.FieldError> fieldErrors) {
        return newApiError(status, code, message, request.getRequestURI(), fieldErrors);
    }

    private ApiError newApiError(HttpStatus status, String code, String message, String path,
                                  List<ApiError.FieldError> fieldErrors) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                fieldErrors
        );
    }
}
