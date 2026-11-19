package ch.alpian.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.alpian.payment.application.tasks.CallSettlementRailTask;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.SettlementOutcome;
import ch.alpian.payment.domain.SettlementRail;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallSettlementRailTaskTest {

    private static final Payment PAYMENT = Payment.reserved(
            "k",
            "h".repeat(64),
            UUID.randomUUID(),
            Receiver.external("CH9300762011623852957"),
            Money.of("10.00", "CHF"));

    @Test
    void call_settlement_rail_returns_unknown_on_exception_or_interruption() {
        SettlementRail rail = mock(SettlementRail.class);
        when(rail.settle(any())).thenThrow(new IllegalStateException("rail exploded"));

        var task = new CallSettlementRailTask(rail, Duration.ofSeconds(1));
        assertThat(task.execute(PAYMENT)).isEqualTo(SettlementOutcome.unknown());

        doAnswer(inv -> {
                    Thread.sleep(500);
                    return SettlementOutcome.settled("OK");
                })
                .when(rail)
                .settle(any());
        Thread.currentThread().interrupt();
        try {
            assertThat(task.execute(PAYMENT)).isEqualTo(SettlementOutcome.unknown());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear interrupt flag
        }
    }

    @Test
    void call_settlement_rail_returns_unknown_on_timeout() {
        SettlementRail rail = mock(SettlementRail.class);
        when(rail.settle(any())).thenAnswer(inv -> {
            Thread.sleep(500);
            return SettlementOutcome.settled("OK");
        });

        var task = new CallSettlementRailTask(rail, Duration.ofMillis(50));
        assertThat(task.execute(PAYMENT)).isEqualTo(SettlementOutcome.unknown());
    }
}
