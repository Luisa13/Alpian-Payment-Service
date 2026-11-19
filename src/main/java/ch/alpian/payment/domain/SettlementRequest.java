package ch.alpian.payment.domain;

import java.util.UUID;

public record SettlementRequest(UUID paymentId, UUID senderAccountId, Receiver receiver, Money amount) {

    public static SettlementRequest from(Payment payment) {
        return new SettlementRequest(
                payment.getId(), payment.getSenderAccountId(), payment.getReceiver(), payment.getAmount());
    }
}
