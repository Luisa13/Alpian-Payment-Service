package ch.example.template.api;

import ch.example.template.api.generated.PaymentsApi;
import ch.example.template.api.generated.model.CreatePaymentRequest;
import ch.example.template.api.generated.model.PaymentResponse;
import ch.example.template.application.flows.CreatePaymentFlow;
import ch.example.template.application.flows.CreatePaymentFlow.PaymentResult;
import ch.example.template.application.flows.GetPaymentFlow;
import ch.example.template.domain.Money;
import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController implements PaymentsApi {

    private final CreatePaymentFlow createPaymentFlow;
    private final GetPaymentFlow getPaymentFlow;
    private final ObjectMapper objectMapper;

    public PaymentController(
            CreatePaymentFlow createPaymentFlow,
            GetPaymentFlow getPaymentFlow,
            ObjectMapper objectMapper) {
        this.createPaymentFlow = createPaymentFlow;
        this.getPaymentFlow = getPaymentFlow;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<PaymentResponse> createPayment(
            UUID idempotencyKey, CreatePaymentRequest createPaymentRequest, String xRequestId) {

        String requestHash = sha256(serialize(createPaymentRequest));
        Money amount = new Money(new BigDecimal(createPaymentRequest.getAmount()), createPaymentRequest.getCurrency());

        var args = new CreatePaymentFlow.Arguments(
                amount,
                createPaymentRequest.getSourceAccountId(),
                createPaymentRequest.getTargetAccountId(),
                createPaymentRequest.getDescription(),
                idempotencyKey.toString(),
                requestHash);

        PaymentResult result = createPaymentFlow.initiate(args);

        return switch (result) {
            case PaymentResult.Created c ->
                    ResponseEntity.status(HttpStatus.CREATED).body(toResponse(c.payment()));
            case PaymentResult.Replayed r -> {
                PaymentResponse stored = deserializeResponse(r.responseBody());
                yield ResponseEntity.status(r.responseStatus()).body(stored);
            }
        };
    }

    @Override
    public ResponseEntity<PaymentResponse> getPayment(UUID id, String xRequestId) {
        Payment payment = getPaymentFlow.initiate(new GetPaymentFlow.Arguments(id));
        return ResponseEntity.ok(toResponse(payment));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PaymentResponse toResponse(Payment p) {
        var resp = new PaymentResponse();
        resp.setId(p.getId());
        resp.setAmount(p.getAmount().amount().toPlainString());
        resp.setCurrency(p.getAmount().currency());
        resp.setStatus(PaymentResponse.StatusEnum.valueOf(p.getPaymentStatus().code()));
        if (p.getPaymentStatus() instanceof PaymentStatus.Failed f) {
            resp.setFailureReason(f.reason());
        }
        resp.setSourceAccountId(p.getSourceAccountId());
        resp.setTargetAccountId(p.getTargetAccountId());
        resp.setDescription(p.getDescription());
        resp.setCreatedAt(p.getCreatedAt() != null ? OffsetDateTime.ofInstant(p.getCreatedAt(), ZoneOffset.UTC) : null);
        resp.setUpdatedAt(p.getUpdatedAt() != null ? OffsetDateTime.ofInstant(p.getUpdatedAt(), ZoneOffset.UTC) : null);
        return resp;
    }

    private static String sha256(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise request for idempotency hash", e);
        }
    }

    private PaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialise stored idempotency response", e);
        }
    }
}
