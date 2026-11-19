package ch.alpian.payment.notification;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Dedupe store for notifications, backed by {@code sent_notifications} (primary key = event id).
 * A second delivery of the same event hits the key and is skipped.
 */
@Component
class SentNotificationStore {

    private final JdbcClient jdbc;

    SentNotificationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code true} if this call recorded the notification, {@code false} if it was already sent */
    boolean tryRecord(UUID eventId, UUID paymentId, UUID accountId, String eventType, String message) {
        int inserted = jdbc.sql("""
                        INSERT INTO sent_notifications (event_id, payment_id, account_id, event_type, message)
                        VALUES (:eventId, :paymentId, :accountId, :eventType, :message)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("paymentId", paymentId)
                .param("accountId", accountId)
                .param("eventType", eventType)
                .param("message", message)
                .update();
        return inserted == 1;
    }
}
