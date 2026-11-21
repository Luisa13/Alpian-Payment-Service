package ch.alpian.payment.application.tasks;

import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import ch.alpian.payment.domain.SettlementRequest;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Task: call the settlement rail with a timeout. Runs outside any transaction: no database lock or
 * connection may be held during the rail call.
 *
 * <p>A timeout, an interruption or any error means the money may have moved, so it is reported
 * as {@code UNKNOWN} and never as a failure.
 */
@Component
public class CallSettlementRailTask implements Task<Payment, SettlementOutcome> {

    private static final Logger log = LoggerFactory.getLogger(CallSettlementRailTask.class);

    private final SettlementRail rail;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public CallSettlementRailTask(SettlementRail rail, @Value("${app.rail.timeout}") Duration timeout) {
        this.rail = rail;
        this.timeout = timeout;
    }

    @Override
    public SettlementOutcome execute(Payment payment) {
        return callWithTimeout(payment, () -> rail.settle(SettlementRequest.from(payment)), "Rail call");
    }

    /**
     * Queries the rail for the outcome of a previously submitted payment without re-submitting it.
     * Returns {@code UNKNOWN} on timeout or error, identical to {@link #execute}.
     */
    public SettlementOutcome getStatus(Payment payment) {
        return callWithTimeout(payment, () -> rail.getStatus(SettlementRequest.from(payment)), "Rail status check");
    }

    private SettlementOutcome callWithTimeout(
            Payment payment, java.util.concurrent.Callable<SettlementOutcome> call, String label) {
        Future<SettlementOutcome> future = executor.submit(call);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("{} timed out after {} for payment {}", label, timeout, payment.getId());
            return SettlementOutcome.unknown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return SettlementOutcome.unknown();
        } catch (ExecutionException e) {
            log.warn("{} failed for payment {}", label, payment.getId(), e.getCause());
            return SettlementOutcome.unknown();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
