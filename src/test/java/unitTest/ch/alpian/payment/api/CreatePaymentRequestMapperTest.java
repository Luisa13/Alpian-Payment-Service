package ch.alpian.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreatePaymentRequestMapperTest {

    // Assumption: no upper bound on payment amount is enforced at the application or API layer.
    // JSON structure and UUID/IBAN format errors are caught by Bean Validation before the mapper runs.

    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID RECEIVER = UUID.randomUUID();

    private static CreatePaymentRequest validRequest() {
        return new CreatePaymentRequest("10.00", "CHF", SENDER).receiverAccountId(RECEIVER);
    }

    @Test
    void missing_idempotency_key_is_rejected() {
        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments(null, validRequest()))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("Idempotency-Key"));

        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments("   ", validRequest()))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("Idempotency-Key"));
    }

    @Test
    void zero_amount_is_rejected() {
        CreatePaymentRequest req = new CreatePaymentRequest("0", "CHF", SENDER).receiverAccountId(RECEIVER);

        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments("key", req))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("amount"));
    }

    @Test
    void negative_amount_is_rejected() {
        CreatePaymentRequest req = new CreatePaymentRequest("-1.00", "CHF", SENDER).receiverAccountId(RECEIVER);

        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments("key", req))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("amount"));
    }

    @Test
    void amount_with_too_many_decimal_places_is_rejected() {
        // CHF supports at most 2 decimal places; 3 must be rejected.
        CreatePaymentRequest req = new CreatePaymentRequest("10.123", "CHF", SENDER).receiverAccountId(RECEIVER);

        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments("key", req))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("amount"));
    }

    @Test
    void unsupported_currency_is_rejected() {
        CreatePaymentRequest req = new CreatePaymentRequest("10.00", "XYZ", SENDER).receiverAccountId(RECEIVER);

        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments("key", req))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("currency"));
    }

    @Test
    void sender_equal_to_receiver_is_rejected() {
        CreatePaymentRequest req = new CreatePaymentRequest("10.00", "CHF", SENDER).receiverAccountId(SENDER);

        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments("key", req))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("receiverAccountId"));
    }

    @Test
    void both_receiver_fields_set_is_rejected() {
        CreatePaymentRequest req = new CreatePaymentRequest("10.00", "CHF", SENDER)
                .receiverAccountId(RECEIVER)
                .receiverIban("CH9300762011623852957");

        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments("key", req))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("receiver"));
    }

    @Test
    void neither_receiver_field_set_is_rejected() {
        CreatePaymentRequest req = new CreatePaymentRequest("10.00", "CHF", SENDER);

        assertThatThrownBy(() -> CreatePaymentRequestMapper.toArguments("key", req))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(e -> assertThat(((RequestValidationException) e).getViolations())
                        .containsKey("receiver"));
    }
}
