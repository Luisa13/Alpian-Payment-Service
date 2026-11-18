package ch.example.template.domain;

public sealed interface PaymentStatus permits PaymentStatus.Requested, PaymentStatus.Settled, PaymentStatus.Failed {

    record Requested() implements PaymentStatus {}

    record Settled() implements PaymentStatus {}

    record Failed(String reason) implements PaymentStatus {
        public Failed {
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("failure reason must not be blank");
        }
    }

    default String code() {
        return switch (this) {
            case Requested r -> "REQUESTED";
            case Settled s -> "SETTLED";
            case Failed f -> "FAILED";
        };
    }

    static PaymentStatus fromCode(String code, String failureReason) {
        return switch (code) {
            case "REQUESTED" -> new Requested();
            case "SETTLED" -> new Settled();
            case "FAILED" -> new Failed(failureReason != null ? failureReason : "unknown");
            default -> throw new IllegalArgumentException("Unknown payment status code: " + code);
        };
    }
}
