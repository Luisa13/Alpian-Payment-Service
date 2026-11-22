package ch.alpian.payment.infrastructure.rail;

import ch.alpian.payment.application.support.HmacSignature;
import ch.alpian.payment.application.support.RailCallbackProperties;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Simulated external rail dispatcher.
 *
 * <p>Periodically polls {@code rail_sim_transfers} for rows whose {@code deliver_at <= now()} and
 * {@code delivered_at IS NULL}, decides the final outcome ({@code SETTLED} or {@code REJECTED}),
 * marks the row delivered, and POSTs a signed callback to {@code /internal/rail/callbacks}.
 * Because {@code deliver_at} is persisted in PostgreSQL, pending callbacks survive restarts.
 */
@Component
class SimulatedCallbackDispatcher implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(SimulatedCallbackDispatcher.class);
    private static final int BATCH_SIZE = 50;

    private final JdbcClient jdbc;
    private final SimulatedRailProperties properties;
    private final RailCallbackProperties callbackProperties;
    private final RestClient restClient;
    private final RandomGenerator random = new SecureRandom();
    private final AtomicInteger localPort = new AtomicInteger(0);

    SimulatedCallbackDispatcher(
            JdbcClient jdbc, SimulatedRailProperties properties, RailCallbackProperties callbackProperties) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.callbackProperties = callbackProperties;
        this.restClient = RestClient.create();
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        localPort.set(event.getWebServer().getPort());
    }

    @Scheduled(fixedDelayString = "${app.rail.simulated.dispatch-interval-ms}")
    public void dispatch() {
        String targetUrl = resolveTargetUrl();
        if (targetUrl == null) {
            return;
        }

        List<UUID> due =
                jdbc.sql("""
                        SELECT payment_id
                        FROM rail_sim_transfers
                        WHERE deliver_at <= now()
                          AND delivered_at IS NULL
                        ORDER BY deliver_at
                        LIMIT :limit
                        """).param("limit", BATCH_SIZE).query(UUID.class).list();

        for (UUID paymentId : due) {
            deliverOne(paymentId, targetUrl);
        }
    }

    private void deliverOne(UUID paymentId, String targetUrl) {
        String outcome = random.nextDouble() < properties.rejectionRate() ? "REJECTED" : "SETTLED";
        String reference = "SETTLED".equals(outcome)
                ? "SIM-" + UUID.randomUUID().toString().substring(0, 8)
                : null;

        int updated = jdbc.sql("""
                        UPDATE rail_sim_transfers
                        SET outcome = :outcome, reference = :reference, delivered_at = now()
                        WHERE payment_id = :id AND delivered_at IS NULL
                        """)
                .param("outcome", outcome)
                .param("reference", reference)
                .param("id", paymentId)
                .update();
        if (updated == 0) {
            return;
        }

        if (random.nextDouble() < properties.dropRate()) {
            log.debug("Chaos drop: suppressing callback for payment {}", paymentId);
            return;
        }

        UUID eventId = UUID.randomUUID();
        postCallback(targetUrl, eventId, paymentId, outcome, reference);

        if (random.nextDouble() < properties.duplicateRate()) {
            log.debug("Chaos duplicate: resending callback for payment {}", paymentId);
            postCallback(targetUrl, eventId, paymentId, outcome, reference);
        }
    }

    private void postCallback(String targetUrl, UUID eventId, UUID paymentId, String outcome, String reference) {
        String signature = HmacSignature.sign(callbackProperties.hmacSecret(), eventId, paymentId, outcome, reference);
        var payload = new CallbackPayload(eventId, paymentId, outcome, reference);
        try {
            restClient
                    .post()
                    .uri(targetUrl)
                    .header("X-Rail-Signature", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to deliver simulated callback for payment {}: {}", paymentId, e.getMessage());
        }
    }

    private String resolveTargetUrl() {
        int port = localPort.get();
        if (port > 0) {
            return "http://localhost:" + port + "/internal/rail/callbacks";
        }
        return properties.callbackUrl();
    }

    private record CallbackPayload(UUID eventId, UUID paymentId, String outcome, String railReference) {}
}
