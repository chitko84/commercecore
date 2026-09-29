package com.commercecore.common.exception;

/**
 * Thrown when a requested resource/application entity does not exist.
 * Handled by {@link com.commercecore.common.web.GlobalExceptionHandler}
 * and mapped to HTTP 404.
 *
 * This is the generic Phase-1 "not found" signal for future modules to
 * throw; it is not tied to any specific business domain.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
