package ch.alpian.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alpian.payment.application.flows.FinalizeSettlementFlow;
import ch.alpian.payment.application.flows.ReserveFundsFlow;
import ch.alpian.payment.application.support.RequestHash;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.PaymentStatus;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.SettlementOutcome;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

/** T8: guarded transition, ordered locks, ledger movements, and outbox events in tx2. */
class SettlementFinalizerIT extends PaymentIntegrationTest {

    @Autowired
    ReserveFundsFlow reserveFunds;

    @Autowired
    FinalizeSettlementFlow finalizeSettlement;

    @Test
    @Timeout(60)
    void two_concurrent_finalizations_leave_one_transition_two_ledger_rows_and_one_event() throws Exception {
        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");
        Payment reserved = reserve(sender, Receiver.internal(receiver), "25.00");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Payment> f1 = pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(15, TimeUnit.SECONDS)).isTrue();
                return finalizeSettlement.finalizeSettlement(reserved.getId(), SettlementOutcome.settled("REF-1"));
            });
            Future<Payment> f2 = pool.submit(() -> {
                ready.countDown();
                assertThat(start.await(15, TimeUnit.SECONDS)).isTrue();
                return finalizeSettlement.finalizeSettlement(reserved.getId(), SettlementOutcome.settled("REF-1"));
            });

            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Payment p1 = f1.get(30, TimeUnit.SECONDS);
            Payment p2 = f2.get(30, TimeUnit.SECONDS);
            assertThat(p1.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(p2.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        assertThat(balance(sender)).isEqualByComparingTo("75.00");
        assertThat(available(sender)).isEqualByComparingTo("75.00");
        assertThat(balance(receiver)).isEqualByComparingTo("25.00");
        assertThat(ledgerRows(reserved.getId())).isEqualTo(2);
        assertThat(outboxEvents(reserved.getId(), "PAYMENT_COMPLETED")).isEqualTo(1);
    }

    @Test
    void rejected_outcome_releases_hold_and_writes_payment_failed_event() {
        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");
        Payment reserved = reserve(sender, Receiver.internal(receiver), "25.00");
        assertThat(available(sender)).isEqualByComparingTo("75.00");

        Payment finalized = finalizeSettlement.finalizeSettlement(reserved.getId(), SettlementOutcome.rejected());

        assertThat(finalized.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(balance(sender)).isEqualByComparingTo("100.00");
        assertThat(available(sender)).isEqualByComparingTo("100.00");
        assertThat(balance(receiver)).isEqualByComparingTo("0.00");
        assertThat(ledgerRows(reserved.getId())).isZero();
        assertThat(outboxEvents(reserved.getId(), "PAYMENT_FAILED")).isEqualTo(1);
    }

    @Test
    void unknown_outcome_leaves_payment_reserved_and_increments_attempts() {
        UUID sender = newAccount("100.00");
        UUID receiver = newAccount("0.00");
        Payment reserved = reserve(sender, Receiver.internal(receiver), "25.00");

        Payment afterFirst = finalizeSettlement.finalizeSettlement(reserved.getId(), SettlementOutcome.unknown());
        assertThat(afterFirst.getStatus()).isEqualTo(PaymentStatus.RESERVED);

        Integer attempts = jdbc.sql("SELECT settlement_attempts FROM payments WHERE id = :id")
                .param("id", reserved.getId())
                .query(Integer.class)
                .single();
        assertThat(attempts).isEqualTo(1);
        assertThat(available(sender)).isEqualByComparingTo("75.00");
        assertThat(balance(sender)).isEqualByComparingTo("100.00");
        assertThat(outboxEvents(reserved.getId(), "PAYMENT_COMPLETED")).isZero();
        assertThat(outboxEvents(reserved.getId(), "PAYMENT_FAILED")).isZero();
    }

    @Test
    void external_settlement_credits_chf_clearing_account() {
        UUID sender = newAccount("100.00");
        BigDecimal clearingBefore = totalClearingBalance();
        Payment reserved = reserve(sender, Receiver.external("CH9300762011623852957"), "30.00");

        Payment finalized = finalizeSettlement.finalizeSettlement(reserved.getId(), SettlementOutcome.settled("EXT-1"));

        assertThat(finalized.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(balance(sender)).isEqualByComparingTo("70.00");
        assertThat(totalClearingBalance()).isEqualByComparingTo(clearingBefore.add(new BigDecimal("30.00")));
        assertThat(ledgerRows(reserved.getId())).isEqualTo(2);
    }

    private Payment reserve(UUID sender, Receiver receiver, String amount) {
        Money money = Money.of(amount, "CHF");
        String key = "fin-" + UUID.randomUUID();
        String hash = RequestHash.of(sender, receiver, money);
        ReserveFundsFlow.Result result =
                reserveFunds.reserve(new ReserveFundsFlow.Arguments(key, hash, sender, receiver, money));
        assertThat(result).isInstanceOf(ReserveFundsFlow.Result.Reserved.class);
        return result.payment();
    }
}
