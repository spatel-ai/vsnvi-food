package com.vsnvi.vsnvi_food.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a {@code traceId} into the MDC for every request, so it appears in every log line and in every error body, and
 * returns it as {@code X-Trace-Id}. A well-formed incoming {@code X-Trace-Id} (e.g. from a gateway) is kept; anything
 * else is replaced, so clients can't inject arbitrary text into the logs. Runs before the security chain, so 401 and
 * 403 bodies carry it too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var incoming = request.getHeader(HEADER);
        var traceId = incoming != null && VALID.matcher(incoming).matches() ? incoming : newTraceId();
        MDC.put(ProblemDetailFactory.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(ProblemDetailFactory.TRACE_ID_MDC_KEY);
        }
    }

    private static String newTraceId() {
        var bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
