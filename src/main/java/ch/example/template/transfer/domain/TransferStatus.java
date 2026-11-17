package ch.example.template.transfer.domain;

/**
 * Sealed transfer status hierarchy. Each variant is a record so it can be pattern-matched with
 * exhaustiveness checking in switch expressions.
 *
 * <p>Permitted transitions:
 *
 * <pre>
 *   Requested ──▶ Settled
 *   Requested ──▶ Failed
 * </pre>
 *
 * All other transitions throw {@link IllegalStateException}.
 */
public sealed interface TransferStatus permits TransferStatus.Requested, TransferStatus.Settled, TransferStatus.Failed {

    record Requested() implements TransferStatus {}

    record Settled() implements TransferStatus {}

    record Failed(String reason) implements TransferStatus {
        public Failed {
            if (reason == null || reason.isBlank())
                throw new IllegalArgumentException("failure reason must not be blank");
        }
    }

    /** Human-readable discriminator stored in the database. */
    default String code() {
        return switch (this) {
            case Requested r -> "REQUESTED";
            case Settled s -> "SETTLED";
            case Failed f -> "FAILED";
        };
    }

    static TransferStatus fromCode(String code, String failureReason) {
        return switch (code) {
            case "REQUESTED" -> new Requested();
            case "SETTLED" -> new Settled();
            case "FAILED" -> new Failed(failureReason != null ? failureReason : "unknown");
            default -> throw new IllegalArgumentException("Unknown transfer status code: " + code);
        };
    }
}
