package ch.alpian.payment.infrastructure.persistence;

import ch.alpian.payment.domain.RailCallbackRepository;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRailCallbackRepository implements RailCallbackRepository {

    private final JdbcClient jdbc;

    JdbcRailCallbackRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean record(UUID eventId, UUID paymentId, String outcome) {
        int inserted = jdbc.sql("""
                        INSERT INTO rail_callbacks (event_id, payment_id, outcome)
                        VALUES (:eventId, :paymentId, :outcome)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("paymentId", paymentId)
                .param("outcome", outcome)
                .update();
        return inserted == 1;
    }
}
