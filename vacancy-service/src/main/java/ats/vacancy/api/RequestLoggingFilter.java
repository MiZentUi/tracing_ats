package ats.vacancy.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Единый идентификатор запроса в MDC: все строки лога одного HTTP-вызова связаны между собой,
 * включая логи LLM-клиента. Полный текст запроса и PII не логируются.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_KEY = "requestId";

    private static final String INCOMING_HEADER = "X-Request-Id";

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
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_KEY, requestId);
        long startedAt = System.nanoTime();
        try {
            log.debug(
                    "HTTP request started: method={}, path={}, query={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getQueryString() == null ? "-" : request.getQueryString());
            chain.doFilter(request, response);
        } finally {
            log.info(
                    "HTTP request completed: method={}, path={}, status={}, elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(INCOMING_HEADER);
        if (header != null && !header.isBlank() && header.length() <= 64) {
            return header.strip();
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
