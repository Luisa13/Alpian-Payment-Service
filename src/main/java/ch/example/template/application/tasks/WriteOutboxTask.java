package ch.example.template.application.tasks;

import ch.example.template.infrastructure.persistence.OutboxEntry;
import ch.example.template.infrastructure.persistence.OutboxRepository;
import org.springframework.stereotype.Component;

/**
 * Task: append one entry to the transactional outbox table.
 *
 * <p>Must run inside the same transaction as the business operation so the
 * outbox row is written atomically with the domain state change.
 */
@Component
public class WriteOutboxTask implements Task<WriteOutboxTask.Arguments, Void> {

    private final OutboxRepository outbox;

    public WriteOutboxTask(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    public record Arguments(String topic, String messageKey, String payload) {}

    @Override
    public Void execute(Arguments args) {
        outbox.save(OutboxEntry.of(args.topic(), args.messageKey(), args.payload()));
        return null;
    }
}
