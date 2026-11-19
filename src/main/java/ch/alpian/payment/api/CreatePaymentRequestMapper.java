package ch.alpian.payment.api;

import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.application.flows.CreatePaymentFlow;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Receiver;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Syntactic validation of a create-payment request, then mapping to the flow's arguments.
 *
 * <p>Bean Validation already checked formats (amount pattern, currency, IBAN shape). This checks
 * what a pattern cannot: amount greater than zero, a supported currency, exactly one receiver, and
 * sender different from receiver. All problems are reported together.
 */
final class CreatePaymentRequestMapper {

    private CreatePaymentRequestMapper() {}

    static CreatePaymentFlow.Arguments toArguments(String idempotencyKey, CreatePaymentRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            violations.put("Idempotency-Key", "must not be blank");
        }

        Money amount = parseAmount(request, violations);
        Receiver receiver = parseReceiver(request, violations);

        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
        return new CreatePaymentFlow.Arguments(idempotencyKey, request.getSenderAccountId(), receiver, amount);
    }

    private static Money parseAmount(CreatePaymentRequest request, Map<String, String> violations) {
        BigDecimal value = new BigDecimal(request.getAmount());
        if (value.signum() <= 0) {
            violations.put("amount", "must be greater than zero");
            return null;
        }
        try {
            return new Money(value, request.getCurrency());
        } catch (ArithmeticException e) {
            violations.put("amount", "has too many decimal places for " + request.getCurrency());
        } catch (IllegalArgumentException e) {
            violations.put("currency", "is not supported: " + request.getCurrency());
        }
        return null;
    }

    private static Receiver parseReceiver(CreatePaymentRequest request, Map<String, String> violations) {
        UUID accountId = request.getReceiverAccountId();
        String iban = request.getReceiverIban();

        if ((accountId == null) == (iban == null)) {
            violations.put("receiver", "exactly one of receiverAccountId and receiverIban is required");
            return null;
        }
        if (accountId != null) {
            if (accountId.equals(request.getSenderAccountId())) {
                violations.put("receiverAccountId", "must differ from senderAccountId");
                return null;
            }
            return Receiver.internal(accountId);
        }
        return Receiver.external(iban.replace(" ", "").toUpperCase(Locale.ROOT));
    }
}
