package com.vsnvi.vsnvi_food.common.web;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 9457 {@link ProblemDetail} with the {@code code}, {@code traceId}, {@code timestamp}
 * and (for validation) {@code errors} extensions from {@code docs/API-DESIGN.md} §4. Responses never carry stack
 * traces, SQL or class names.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String TRACE_ID_MDC_KEY = "traceId";

    private static final Map<Integer, ErrorCode> CODES_BY_STATUS = Map.of(
            400, ErrorCode.MALFORMED_REQUEST,
            401, ErrorCode.UNAUTHORIZED,
            403, ErrorCode.FORBIDDEN,
            404, ErrorCode.RESOURCE_NOT_FOUND,
            405, ErrorCode.METHOD_NOT_ALLOWED,
            406, ErrorCode.NOT_ACCEPTABLE,
            413, ErrorCode.PAYLOAD_TOO_LARGE,
            415, ErrorCode.UNSUPPORTED_MEDIA_TYPE);

    private final Clock clock;

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        return problem(ex.errorCode(), ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(ErrorCode.CONCURRENT_MODIFICATION, "The resource was modified concurrently; retry");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(ErrorCode.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(ErrorCode.UNAUTHORIZED, "Authentication required");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(e ->
                                new FieldError(result.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /**
     * Framework exceptions (unreadable body, wrong method, missing route...) end up here. The response always uses the
     * catalog status of the chosen code, so {@code status}, {@code code} and {@code type} never disagree: an unmapped
     * 4xx becomes {@code MALFORMED_REQUEST} (400) and any 5xx becomes {@code INTERNAL_ERROR} (500).
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        var code = statusCode.is5xxServerError()
                ? ErrorCode.INTERNAL_ERROR
                : CODES_BY_STATUS.getOrDefault(statusCode.value(), ErrorCode.MALFORMED_REQUEST);
        if (statusCode.is5xxServerError()) {
            log.error("Request failed", ex);
        }
        // Framework details can echo Java types (e.g. type-mismatch messages), so use a fixed detail.
        return ResponseEntity.status(code.status()).headers(headers).body(problem(code, code.title()));
    }

    private ProblemDetail validationProblem(List<FieldError> errors) {
        var problem = problem(ErrorCode.VALIDATION_FAILED, "Request has %d invalid fields".formatted(errors.size()));
        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.status()), detail);
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now(clock));
        var traceId = MDC.get(TRACE_ID_MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    record FieldError(String field, String message) {}
}
