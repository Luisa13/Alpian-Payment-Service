package ch.alpian.payment.api;

import ch.alpian.payment.api.generated.model.AccountResponse;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.Payment;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Domain objects to API models. */
final class ApiMapper {

    private ApiMapper() {}

    static PaymentResponse toResponse(Payment p) {
        var response = new PaymentResponse();
        response.setId(p.getId());
        response.setAmount(p.getAmount().amount().toPlainString());
        response.setCurrency(p.getAmount().currency());
        response.setStatus(PaymentResponse.StatusEnum.valueOf(p.getStatus().name()));
        response.setFailureReason(
                p.getFailureReason() == null ? null : p.getFailureReason().name());
        response.setSenderAccountId(p.getSenderAccountId());
        response.setReceiverAccountId(p.getReceiver().accountId());
        response.setReceiverIban(p.getReceiver().iban());
        response.setRailReference(p.getRailReference());
        response.setCreatedAt(utc(p.getCreatedAt()));
        response.setUpdatedAt(utc(p.getUpdatedAt()));
        return response;
    }

    static AccountResponse toResponse(Account a) {
        var response = new AccountResponse();
        response.setId(a.getId());
        response.setOwnerName(a.getOwnerName());
        response.setAccountType(
                AccountResponse.AccountTypeEnum.valueOf(a.getType().name()));
        response.setCurrency(a.getCurrency());
        response.setBalance(a.getBalance().amount().toPlainString());
        response.setAvailableBalance(a.getAvailableBalance().amount().toPlainString());
        return response;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
