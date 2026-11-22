package ch.alpian.payment.api;

/** Raised when an incoming rail callback fails HMAC-SHA256 signature verification. */
public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException() {
        super("Invalid or missing rail callback signature");
    }
}
