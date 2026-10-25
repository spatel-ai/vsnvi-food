package com.vsnvi.vsnvi_food.common.web;

import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Builds every error body in the shape of {@code docs/API-DESIGN.md} §4. */
@Component
@RequiredArgsConstructor
public class ProblemDetailFactory {

    static final String TRACE_ID_MDC_KEY = "traceId";

    private final Clock clock;

    public ProblemDetail create(ErrorCode code, String detail) {
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
}
