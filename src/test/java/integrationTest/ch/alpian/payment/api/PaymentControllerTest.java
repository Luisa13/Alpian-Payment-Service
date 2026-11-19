package ch.alpian.payment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.alpian.payment.application.flows.CreatePaymentFlow;
import ch.alpian.payment.application.flows.GetAccountFlow;
import ch.alpian.payment.application.flows.GetPaymentFlow;
import ch.alpian.payment.application.flows.PaymentResult;
import ch.alpian.payment.domain.Account;
import ch.alpian.payment.domain.AccountType;
import ch.alpian.payment.domain.CurrencyMismatchException;
import ch.alpian.payment.domain.EntityNotFoundException;
import ch.alpian.payment.domain.FailureReason;
import ch.alpian.payment.domain.IdempotencyConflictException;
import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Payment;
import ch.alpian.payment.domain.Receiver;
import ch.alpian.payment.domain.UnknownAccountException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** T4: every validation error and every status code of the REST API. No database, no Kafka. */
@WebMvcTest({PaymentController.class, AccountController.class})
class PaymentControllerTest {

    private static final String SENDER = "a0000000-0000-0000-0000-000000000001";
    private static final String RECEIVER = "a0000000-0000-0000-0000-000000000002";
    private static final String IBAN = "CH9300762011623852957";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CreatePaymentFlow createPaymentFlow;

    @MockitoBean
    GetPaymentFlow getPaymentFlow;

    @MockitoBean
    GetAccountFlow getAccountFlow;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static String body(String amount, String currency, String sender, String receiverAccount, String iban) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"amount\":").append(amount == null ? "null" : "\"" + amount + "\"");
        json.append(",\"currency\":").append(currency == null ? "null" : "\"" + currency + "\"");
        json.append(",\"senderAccountId\":").append(sender == null ? "null" : "\"" + sender + "\"");
        if (receiverAccount != null)
            json.append(",\"receiverAccountId\":\"").append(receiverAccount).append("\"");
        if (iban != null) json.append(",\"receiverIban\":\"").append(iban).append("\"");
        return json.append("}").toString();
    }

    private static String validBody() {
        return body("100.00", "CHF", SENDER, RECEIVER, null);
    }

    private static MockHttpServletRequestBuilder createRequest(String key, String json) {
        var request =
                post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(json);
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private static Payment reserved() {
        return Payment.reserved(
                "key",
                "h".repeat(64),
                UUID.fromString(SENDER),
                Receiver.internal(UUID.fromString(RECEIVER)),
                Money.of("100.00", "CHF"));
    }

    private static Payment completed() {
        Payment p = reserved();
        p.complete("INT-1");
        return p;
    }

    private static Payment failed() {
        Payment p = reserved();
        p.fail(FailureReason.RAIL_REJECTED);
        return p;
    }

    private static Payment rejected() {
        return Payment.rejected(
                "key",
                "h".repeat(64),
                UUID.fromString(SENDER),
                Receiver.external(IBAN),
                Money.of("100.00", "CHF"),
                FailureReason.INSUFFICIENT_FUNDS);
    }

    // ─── status codes ────────────────────────────────────────────────────────

    @Test
    void settled_payment_returns_201_with_the_final_status() throws Exception {
        Payment payment = completed();
        when(createPaymentFlow.initiate(any())).thenReturn(new PaymentResult(payment, PaymentResult.Kind.CREATED));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("100.00"))
                .andExpect(jsonPath("$.currency").value("CHF"))
                .andExpect(jsonPath("$.railReference").value("INT-1"))
                .andExpect(jsonPath("$.receiverAccountId").value(RECEIVER));
    }

    @Test
    void rail_rejected_payment_returns_201_with_status_failed() throws Exception {
        when(createPaymentFlow.initiate(any())).thenReturn(new PaymentResult(failed(), PaymentResult.Kind.CREATED));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("RAIL_REJECTED"));
    }

    @Test
    void unconfirmed_settlement_returns_202_with_location() throws Exception {
        Payment payment = reserved();
        when(createPaymentFlow.initiate(any())).thenReturn(new PaymentResult(payment, PaymentResult.Kind.ACCEPTED));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/payments/" + payment.getId()))
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void idempotent_replay_returns_200_with_the_current_status() throws Exception {
        when(createPaymentFlow.initiate(any())).thenReturn(new PaymentResult(completed(), PaymentResult.Kind.REPLAYED));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void rejected_payment_returns_422_with_code_and_payment_id() throws Exception {
        Payment payment = rejected();
        when(createPaymentFlow.initiate(any())).thenReturn(new PaymentResult(payment, PaymentResult.Kind.REJECTED));

        mvc.perform(createRequest("key-1", body("100.00", "CHF", SENDER, null, IBAN)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void reused_idempotency_key_returns_422() throws Exception {
        when(createPaymentFlow.initiate(any())).thenThrow(new IdempotencyConflictException("key-1"));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void unknown_account_returns_422() throws Exception {
        when(createPaymentFlow.initiate(any())).thenThrow(new UnknownAccountException(UUID.fromString(SENDER)));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ACCOUNT"));
    }

    @Test
    void currency_mismatch_returns_422() throws Exception {
        when(createPaymentFlow.initiate(any()))
                .thenThrow(new CurrencyMismatchException(UUID.fromString(SENDER), "CHF", "EUR"));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void deadlock_that_outlasts_the_retries_returns_409() throws Exception {
        when(createPaymentFlow.initiate(any())).thenThrow(new DeadlockLoserDataAccessException("deadlock", null));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void unexpected_error_returns_500_without_details() throws Exception {
        when(createPaymentFlow.initiate(any())).thenThrow(new IllegalStateException("secret internals"));

        mvc.perform(createRequest("key-1", validBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errorReference").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void external_receiver_is_normalized_and_passed_to_the_flow() throws Exception {
        when(createPaymentFlow.initiate(any())).thenReturn(new PaymentResult(completed(), PaymentResult.Kind.CREATED));

        mvc.perform(createRequest("key-1", body("5", "CHF", SENDER, null, "ch93 0076 2011 6238 5295 7")))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<CreatePaymentFlow.Arguments> captor =
                org.mockito.ArgumentCaptor.forClass(CreatePaymentFlow.Arguments.class);
        org.mockito.Mockito.verify(createPaymentFlow).initiate(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().receiver()).isEqualTo(Receiver.external(IBAN));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().amount()).isEqualTo(Money.of("5.00", "CHF"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().idempotencyKey())
                .isEqualTo("key-1");
    }

    // ─── validation errors (400) ─────────────────────────────────────────────

    @Test
    void missing_idempotency_key_returns_400() throws Exception {
        mvc.perform(createRequest(null, validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
        verifyNoInteractions(createPaymentFlow);
    }

    @Test
    void blank_idempotency_key_returns_400() throws Exception {
        mvc.perform(createRequest("   ", validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(createPaymentFlow);
    }

    @Test
    void too_long_idempotency_key_returns_400() throws Exception {
        mvc.perform(createRequest("k".repeat(65), validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(createPaymentFlow);
    }

    @Test
    void missing_required_fields_return_400_with_every_violation() throws Exception {
        mvc.perform(createRequest("key-1", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations.amount").exists())
                .andExpect(jsonPath("$.violations.currency").exists())
                .andExpect(jsonPath("$.violations.senderAccountId").exists());
        verifyNoInteractions(createPaymentFlow);
    }

    @Test
    void malformed_json_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void zero_amount_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("0.00", "CHF", SENDER, RECEIVER, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.amount").value("must be greater than zero"));
        verifyNoInteractions(createPaymentFlow);
    }

    @Test
    void negative_amount_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("-5.00", "CHF", SENDER, RECEIVER, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.amount").exists());
    }

    @Test
    void amount_with_three_decimals_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("1.001", "CHF", SENDER, RECEIVER, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.amount").exists());
    }

    @Test
    void non_numeric_amount_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("ten", "CHF", SENDER, RECEIVER, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.amount").exists());
    }

    @Test
    void malformed_currency_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("1.00", "chf", SENDER, RECEIVER, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.currency").exists());
    }

    @Test
    void unsupported_currency_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("1.00", "XYZ", SENDER, RECEIVER, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.currency").exists());
    }

    @Test
    void both_receivers_return_400() throws Exception {
        mvc.perform(createRequest("key-1", body("1.00", "CHF", SENDER, RECEIVER, IBAN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.receiver").exists());
    }

    @Test
    void no_receiver_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("1.00", "CHF", SENDER, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.receiver").exists());
    }

    @Test
    void sender_equal_to_receiver_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("1.00", "CHF", SENDER, SENDER, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.receiverAccountId").exists());
    }

    @Test
    void malformed_iban_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("1.00", "CHF", SENDER, null, "12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.receiverIban").exists());
    }

    @Test
    void malformed_account_id_returns_400() throws Exception {
        mvc.perform(createRequest("key-1", body("1.00", "CHF", "not-a-uuid", RECEIVER, null)))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /payments/{id} and GET /accounts/{id} ───────────────────────────

    @Test
    void get_payment_returns_200() throws Exception {
        Payment payment = completed();
        when(getPaymentFlow.initiate(any())).thenReturn(payment);

        mvc.perform(get("/api/v1/payments/" + payment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void get_unknown_payment_returns_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getPaymentFlow.initiate(any())).thenThrow(new EntityNotFoundException("Payment", id));

        mvc.perform(get("/api/v1/payments/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void get_payment_with_malformed_id_returns_400() throws Exception {
        mvc.perform(get("/api/v1/payments/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void get_account_returns_both_balances() throws Exception {
        Account account = new Account(
                UUID.fromString(SENDER),
                "Alice",
                AccountType.CUSTOMER,
                Money.of("100.00", "CHF"),
                Money.of("60.00", "CHF"),
                3);
        when(getAccountFlow.initiate(any())).thenReturn(account);

        mvc.perform(get("/api/v1/accounts/" + SENDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SENDER))
                .andExpect(jsonPath("$.ownerName").value("Alice"))
                .andExpect(jsonPath("$.accountType").value("CUSTOMER"))
                .andExpect(jsonPath("$.currency").value("CHF"))
                .andExpect(jsonPath("$.balance").value("100.00"))
                .andExpect(jsonPath("$.availableBalance").value("60.00"));
    }

    @Test
    void get_unknown_account_returns_404() throws Exception {
        when(getAccountFlow.initiate(any())).thenThrow(new EntityNotFoundException("Account", SENDER));

        mvc.perform(get("/api/v1/accounts/" + SENDER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }
}
