package com.commercecore.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error response shape used by every error the API
 * returns (validation, not-found, malformed request, unexpected
 * server error). Never includes a stack trace or other internal
 * details.
 *
 * @param timestamp when the error was produced
 * @param status    HTTP status code
 * @param error     HTTP status reason phrase (e.g. "Bad Request")
 * @param code      short machine-readable error code (e.g.
 *                  "VALIDATION_FAILED", "NOT_FOUND")
 * @param message   human-readable, client-safe message
 * @param path      request path that produced the error
 * @param traceId   correlation id for this request, for cross-referencing
 *                  server logs (see {@link CorrelationIdFilter})
 * @param fieldErrors validation field errors, when relevant; omitted
 *                     from the JSON response otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldError> fieldErrors
) {

    /**
     * A single field-level validation failure.
     *
     * @param field   the offending field name
     * @param message the validation failure message for that field
     */
    public record FieldError(String field, String message) {
    }
}
