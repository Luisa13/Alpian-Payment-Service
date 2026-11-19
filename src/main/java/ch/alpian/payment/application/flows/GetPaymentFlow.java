package ch.alpian.payment.application.flows;

import ch.alpian.payment.application.tasks.FindPaymentByIdTask;
import ch.alpian.payment.domain.Payment;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Flow: retrieve an existing payment by its ID.
 *
 * <p>Delegates entirely to {@link FindPaymentByIdTask}. Exists as a flow so
 * that cross-cutting concerns (logging, auth checks, future caching) can be
 * added here without touching infrastructure or domain code.
 */
@Service
public class GetPaymentFlow {

    private final FindPaymentByIdTask findPaymentById;

    public record Arguments(UUID paymentId) {}

    public GetPaymentFlow(FindPaymentByIdTask findPaymentById) {
        this.findPaymentById = findPaymentById;
    }

    public Payment initiate(Arguments args) {
        return findPaymentById.execute(args.paymentId());
    }
}
