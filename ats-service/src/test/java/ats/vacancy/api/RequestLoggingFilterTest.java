package ats.vacancy.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void logsInboundEventsAndKeepsIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/vacancies");
        request.addHeader(RequestIdsHeader.NAME, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        new RequestLoggingFilter().doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.startsWith("inbound_request_received method=POST"))
                .anyMatch(
                        m ->
                                m.startsWith(
                                        "inbound_response_sent method=POST path=/api/v1/vacancies"
                                                + " status=200 elapsed_ms="));
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    void doesNotLogWebhookTokenFromQueryString() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/integrations/hh/webhook");
        request.setQueryString("token=super-secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        new RequestLoggingFilter().doFilter(request, response, chain);

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("super-secret-token"));
    }

    @Test
    void generatesRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        new RequestLoggingFilter()
                .doFilter(new MockHttpServletRequest("GET", "/api/v1/levels"), response, chain);

        assertThat(response.getHeader("X-Request-Id")).hasSize(8);
    }

    /** Константа заголовка дублируется, чтобы тест не зависел от деталей фильтра. */
    private static final class RequestIdsHeader {
        private static final String NAME = "X-Request-Id";
    }
}
