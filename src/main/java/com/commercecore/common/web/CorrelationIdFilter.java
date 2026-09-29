package com.commercecore.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation/trace id to every request: reuses an inbound
 * {@code X-Request-Id} header if present (so callers/gateways can
 * propagate their own id), otherwise generates one. The id is placed
 * in the SLF4J MDC (so it shows up in every log line for the request,
 * per the {@code logging.pattern.level} pattern in application.yml),
 * echoed back on the response, and made available to
 * {@link com.commercecore.common.web.GlobalExceptionHandler} for the
 * {@code traceId} field on {@link ApiError}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Correlation-Id";
    public static final String LEGACY_TRACE_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) traceId=request.getHeader(LEGACY_TRACE_ID_HEADER);
        if (traceId != null && !traceId.matches("[A-Za-z0-9._:-]{1,64}")) traceId=null;
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            response.setHeader(LEGACY_TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
