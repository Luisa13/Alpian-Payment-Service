package ch.example.template.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts or generates a correlation ID for every inbound HTTP request.
 *
 * <ul>
 *   <li>Reads {@code X-Request-Id} header; generates a UUID if absent.
 *   <li>Stores it in SLF4J MDC as {@code correlationId} so every log line in the request thread
 *       carries it automatically.
 *   <li>Echoes the value back in the response {@code X-Request-Id} header.
 *   <li>The {@code OutboxRelay} picks it up from MDC and writes it into every Kafka
 *       {@code ProducerRecord} header, so the same ID flows end-to-end.
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Always clear MDC to avoid leaking values across virtual-thread re-use
            MDC.remove(MDC_KEY);
        }
    }
}
