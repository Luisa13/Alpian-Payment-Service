package ch.alpian.payment.application.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Computes and verifies HMAC-SHA256 signatures for external rail callbacks. */
public final class HmacSignature {

    private static final String ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    private HmacSignature() {}

    public static String sign(String secret, UUID eventId, UUID paymentId, String outcome, String railReference) {
        String payload = eventId + ":" + paymentId + ":" + outcome + ":" + Objects.toString(railReference, "");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HEX.formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }

    public static boolean verify(
            String secret, String signature, UUID eventId, UUID paymentId, String outcome, String railReference) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(secret, eventId, paymentId, outcome, railReference);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }
}
