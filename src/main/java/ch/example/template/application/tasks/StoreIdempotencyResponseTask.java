package ch.example.template.application.tasks;

import ch.example.template.infrastructure.persistence.IdempotencyStore;
import org.springframework.stereotype.Component;

/**
 * Task: persist the HTTP response in the idempotency store so future replays
 * can return the identical response without re-executing the business logic.
 */
@Component
public class StoreIdempotencyResponseTask implements Task<StoreIdempotencyResponseTask.Arguments, Void> {

    private final IdempotencyStore idempotencyStore;

    public StoreIdempotencyResponseTask(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    public record Arguments(String idempotencyKey, String requestHash, int responseStatus, String responseBody) {}

    @Override
    public Void execute(Arguments args) {
        idempotencyStore.storeResponse(
                args.idempotencyKey(), args.requestHash(), args.responseStatus(), args.responseBody());
        return null;
    }
}
