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

    public CallSettlementRailTask(SettlementRail rail, @Value("${app.rail.timeout:2s}") Duration timeout) {
        this.rail = rail;
        this.timeout = timeout;
    }

    @Override
    public SettlementOutcome execute(Payment payment) {
        Future<SettlementOutcome> call = executor.submit(() -> rail.settle(SettlementRequest.from(payment)));
        try {
            return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            log.warn("Rail call timed out after {} for payment {}", timeout, payment.getId());
            return SettlementOutcome.unknown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.cancel(true);
            return SettlementOutcome.unknown();
        } catch (ExecutionException e) {
            log.warn("Rail call failed for payment {}", payment.getId(), e.getCause());
            return SettlementOutcome.unknown();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
