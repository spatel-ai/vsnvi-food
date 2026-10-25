package com.vsnvi.vsnvi_food.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void should_generate_trace_id_in_mdc_and_header_when_request_has_none() throws Exception {
        var seen = new AtomicReference<String>();
        var response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, chainRecording(seen));

        assertThat(seen.get()).matches("[0-9a-f]{16}");
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(seen.get());
        assertThat(MDC.get(ProblemDetailFactory.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void should_keep_incoming_trace_id_when_well_formed() throws Exception {
        var seen = new AtomicReference<String>();
        var request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "gateway-abc-123");

        filter.doFilter(request, new MockHttpServletResponse(), chainRecording(seen));

        assertThat(seen.get()).isEqualTo("gateway-abc-123");
    }

    @Test
    void should_replace_incoming_trace_id_when_it_could_inject_into_logs() throws Exception {
        var seen = new AtomicReference<String>();
        var request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "abc\nFAKE LOG LINE");

        filter.doFilter(request, new MockHttpServletResponse(), chainRecording(seen));

        assertThat(seen.get()).matches("[0-9a-f]{16}");
    }

    private static MockFilterChain chainRecording(AtomicReference<String> seen) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                seen.set(MDC.get(ProblemDetailFactory.TRACE_ID_MDC_KEY));
            }
        };
    }
}
