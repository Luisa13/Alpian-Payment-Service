package ch.example.template.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.example.template.application.flows.CreatePaymentFlow;
import ch.example.template.application.flows.CreatePaymentFlow.PaymentResult;
import ch.example.template.application.flows.GetPaymentFlow;
import ch.example.template.domain.EntityNotFoundException;
import ch.example.template.domain.IdempotencyConflictException;
import ch.example.template.domain.Money;
import ch.example.template.domain.Payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    CreatePaymentFlow createPaymentFlow;

    @MockitoBean
    GetPaymentFlow getPaymentFlow;

    private static final String VALID_BODY = """
            {
              "amount": "100.00",
              "currency": "CHF",
              "sourceAccountId": "a0000000-0000-0000-0000-000000000001",
              "targetAccountId": "a0000000-0000-0000-0000-000000000002",
              "description": "Rent payment"
            }
            """;

    @Test
    void post_valid_request_returns_201() throws Exception {
        var payment = stubPayment();
        when(createPaymentFlow.initiate(any()))
                .thenReturn(PaymentResult.created(payment));

        mvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void post_missing_idempotency_key_returns_400() throws Exception {
        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_missing_required_fields_returns_400_with_violations() throws Exception {
        String invalidBody = """
                { "description": "missing required fields" }
                """;
        mvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void post_idempotent_replay_returns_201_same_body() throws Exception {
        var stored = new PaymentResult.Replayed(201, "{\"id\":\"" + UUID.randomUUID() + "\"}");
        when(createPaymentFlow.initiate(any())).thenReturn(stored);

        mvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void post_idempotency_conflict_returns_409() throws Exception {
        when(createPaymentFlow.initiate(any()))
                .thenThrow(new IdempotencyConflictException("key-1"));

        mvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void get_existing_payment_returns_200() throws Exception {
        var payment = stubPayment();
        when(getPaymentFlow.initiate(any())).thenReturn(payment);

        mvc.perform(get("/v1/payments/" + payment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()));
    }

    @Test
    void get_unknown_payment_returns_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getPaymentFlow.initiate(any())).thenThrow(new EntityNotFoundException("Payment", id));

        mvc.perform(get("/v1/payments/" + id)).andExpect(status().isNotFound());
    }

    private Payment stubPayment() {
        return Payment.create(
                new Money(new BigDecimal("100.00"), "CHF"),
                UUID.fromString("a0000000-0000-0000-0000-000000000001"),
                UUID.fromString("a0000000-0000-0000-0000-000000000002"),
                "Rent payment");
    }
}
