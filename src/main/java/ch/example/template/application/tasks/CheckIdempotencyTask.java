package ch.example.template.application.tasks;

import ch.example.template.infrastructure.persistence.IdempotencyStore;
import ch.example.template.infrastructure.persistence.IdempotencyStore.StoredResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Task: look up a prior response from the idempotency store.
 *
 * <p>Returns {@link Optional#empty()} when the key is new (no prior response).
 * Throws {@link ch.example.template.domain.IdempotencyConflictException} when the same
 * key was used with a different request body.
 */
@Component
public class CheckIdempotencyTask implements Task<CheckIdempotencyTask.Arguments, Optional<StoredResponse>> {

    private final IdempotencyStore idempotencyStore;

    public CheckIdempotencyTask(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    public record Arguments(String idempotencyKey, String requestHash) {}

    @Override
    public Optional<StoredResponse> execute(Arguments args) {
        return idempotencyStore.findExisting(args.idempotencyKey(), args.requestHash());
    }
}
