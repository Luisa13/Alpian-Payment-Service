package ch.alpian.payment.application.support;

import ch.alpian.payment.domain.Money;
import ch.alpian.payment.domain.Receiver;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * SHA-256 of the normalized request: two requests that mean the same thing (for example amounts
 * {@code 10} and {@code 10.00}) hash the same; any difference in meaning hashes differently.
 */
public final class RequestHash {

    private RequestHash() {}

    public static String of(UUID senderAccountId, Receiver receiver, Money amount) {
        String receiverPart =
                receiver.isInternal() ? "INTERNAL:" + receiver.accountId() : "EXTERNAL:" + receiver.iban();
        String normalized = String.join(
                "|",
                senderAccountId.toString(),
                receiverPart,
                amount.amount().stripTrailingZeros().toPlainString(),
                amount.currency());
        return sha256Hex(normalized);
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
