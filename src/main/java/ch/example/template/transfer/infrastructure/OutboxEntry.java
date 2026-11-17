package ch.example.template.transfer.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Spring Data JDBC entity mapping to the {@code outbox_events} table. */
@Table("outbox_events")
public class OutboxEntry implements Persistable<UUID> {

    @Id
    UUID id;

    @Transient
    private boolean isNew;

    String topic;

    @Column("message_key")
    String messageKey;

    String payload;

    @Column("created_at")
    Instant createdAt;

    boolean sent;

    @Column("sent_at")
    Instant sentAt;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public static OutboxEntry of(String topic, String messageKey, String payload) {
        var e = new OutboxEntry();
        e.id = UUID.randomUUID();
        e.isNew = true;
        e.topic = topic;
        e.messageKey = messageKey;
        e.payload = payload;
        e.createdAt = Instant.now();
        e.sent = false;
        return e;
    }
}
