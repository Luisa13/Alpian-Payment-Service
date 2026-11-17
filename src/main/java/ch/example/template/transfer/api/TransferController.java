package ch.example.template.transfer.api;

import ch.example.template.shared.domain.Money;
import ch.example.template.transfer.api.generated.TransfersApi;
import ch.example.template.transfer.api.generated.model.CreateTransferRequest;
import ch.example.template.transfer.api.generated.model.TransferResponse;
import ch.example.template.transfer.application.TransferService;
import ch.example.template.transfer.application.TransferService.TransferResult;
import ch.example.template.transfer.domain.Transfer;
import ch.example.template.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the sample transfers feature.
 *
 * <p>Implements the generated {@link TransfersApi} interface from the OpenAPI spec — the spec is
 * the source of truth for request/response shapes.
 *
 * <p><b>Idempotency</b>: the raw request body JSON is hashed with SHA-256 here before being passed
 * to the application service. This keeps the hash logic at the HTTP boundary where the raw bytes
 * are available.
 */
@RestController
public class TransferController implements TransfersApi {

    private final TransferService service;
    private final ObjectMapper objectMapper;

    public TransferController(TransferService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResponseEntity<TransferResponse> createTransfer(
            UUID idempotencyKey, CreateTransferRequest createTransferRequest, String xRequestId) {

        String requestHash = sha256(serialize(createTransferRequest));
        Money amount =
                new Money(new BigDecimal(createTransferRequest.getAmount()), createTransferRequest.getCurrency());
        UUID sourceId = createTransferRequest.getSourceAccountId();
        UUID targetId = createTransferRequest.getTargetAccountId();
        String description = createTransferRequest.getDescription();

        var cmd = new TransferService.CreateTransferCommand(amount, sourceId, targetId, description);
        TransferResult result = service.create(cmd, idempotencyKey.toString(), requestHash);

        return switch (result) {
            case TransferResult.Created c ->
                ResponseEntity.status(HttpStatus.CREATED).body(toResponse(c.transfer()));
            case TransferResult.Replayed r -> {
                // Deserialise the stored response body back to TransferResponse
                TransferResponse stored = deserializeResponse(r.responseBody());
                yield ResponseEntity.status(r.responseStatus()).body(stored);
            }
        };
    }

    @Override
    public ResponseEntity<TransferResponse> getTransfer(UUID id, String xRequestId) {
        Transfer transfer = service.findById(id);
        return ResponseEntity.ok(toResponse(transfer));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private TransferResponse toResponse(Transfer t) {
        var resp = new TransferResponse();
        resp.setId(t.getId());
        resp.setAmount(t.getAmount().amount().toPlainString());
        resp.setCurrency(t.getAmount().currency());
        resp.setStatus(TransferResponse.StatusEnum.valueOf(t.getTransferStatus().code()));
        if (t.getTransferStatus() instanceof TransferStatus.Failed f) {
            resp.setFailureReason(f.reason());
        }
        resp.setSourceAccountId(t.getSourceAccountId());
        resp.setTargetAccountId(t.getTargetAccountId());
        resp.setDescription(t.getDescription());
        resp.setCreatedAt(t.getCreatedAt() != null ? OffsetDateTime.ofInstant(t.getCreatedAt(), ZoneOffset.UTC) : null);
        resp.setUpdatedAt(t.getUpdatedAt() != null ? OffsetDateTime.ofInstant(t.getUpdatedAt(), ZoneOffset.UTC) : null);
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

    private TransferResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, TransferResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialise stored idempotency response", e);
        }
    }
}
