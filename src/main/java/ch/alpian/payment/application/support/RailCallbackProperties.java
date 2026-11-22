package ch.alpian.payment.application.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for incoming external rail callbacks.
 *
 * <p>Note: in a real deployment, HMAC signature verification should be paired with mTLS or
 * API-gateway authentication in front of {@code /internal/rail/callbacks}.
 *
 * @param hmacSecret shared secret used to compute and verify {@code X-Rail-Signature}
 */
@ConfigurationProperties("app.rail.callback")
public record RailCallbackProperties(String hmacSecret) {

    public RailCallbackProperties {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("app.rail.callback.hmac-secret must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RailCallbackProperties[hmacSecret=***]";
    }
}
