package ats.vacancy.api;

import ats.support.logging.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Граница входящего HTTP: выдаёт request_id, кладёт его в MDC и заголовок ответа, логирует ровно
 * два события — приём запроса и отправку ответа. Тела запросов и PII не логируются.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = RequestIds.sanitize(request.getHeader(RequestIds.HTTP_HEADER));
        RequestIds.put(requestId);
        response.setHeader(RequestIds.RESPONSE_HEADER, requestId);
        long startedAt = System.nanoTime();
        log.info(
                "inbound_request_received method={} path={}",
                request.getMethod(),
                request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            log.info(
                    "inbound_response_sent method={} path={} status={} elapsed_ms={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            RequestIds.clear();
        }
    }
}
