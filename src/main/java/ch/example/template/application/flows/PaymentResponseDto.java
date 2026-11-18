package ch.example.template.application.flows;

import ch.example.template.domain.Payment;
import ch.example.template.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Serialisable DTO stored in the idempotency table so the controller can replay
 * the identical JSON body on a repeated request.
 * Field names intentionally match the OpenAPI-generated {@code PaymentResponse} model.
 */
public record PaymentResponseDto(
        UUID id,
        String amount,
        String currency,
        String status,
        String failureReason,
        UUID sourceAccountId,
        UUID targetAccountId,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponseDto from(Payment p) {
        String failureReason = (p.getPaymentStatus() instanceof PaymentStatus.Failed f) ? f.reason() : null;
        return new PaymentResponseDto(
                p.getId(),
                p.getAmount().amount().toPlainString(),
                p.getAmount().currency(),
                p.getPaymentStatus().code(),
                failureReason,
                p.getSourceAccountId(),
                p.getTargetAccountId(),
                p.getDescription(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
