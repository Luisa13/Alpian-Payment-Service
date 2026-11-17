package ch.example.template.shared.infrastructure;

import ch.example.template.shared.application.IdempotencyConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * HTTP idempotency store backed by the {@code idempotency_keys} table.
 *
 * <p>Protocol (called from application services, not from controllers):
 *
 * <ol>
 *   <li>Hash the raw request body with SHA-256.
 *   <li>Call {@link #findOrStore} inside the same transaction as the business logic:
 *       <ul>
 *         <li>Returns an empty {@link Optional} → no prior entry; proceed normally, then call
 *             {@link #storeResponse}.
 *         <li>Returns a present {@link Optional} with the same hash → replay the stored response.
 *         <li>Returns a present {@link Optional} with a different hash → throws {@link
 *             IdempotencyConflictException} (HTTP 409).
 *       </ul>
 * </ol>
 *
 * <p>Concurrent duplicates are handled at the database level: the first INSERT wins (UNIQUE
 * constraint on {@code idempotency_key}); subsequent concurrent inserts get a constraint violation
 * and retry by reading the winner's stored response.
 */
@Component
public class IdempotencyStore {

    private final JdbcClient jdbc;

    public IdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** SHA-256 hex digest of the UTF-8 encoded request body. */
    public static String hash(String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Looks up an existing entry. Returns the stored response if found, or empty if this is the
     * first time we see this key.
     *
     * @throws IdempotencyConflictException if the key exists but was used with a different body
     */
    public Optional<StoredResponse> findExisting(String idempotencyKey, String requestHash) {
        return jdbc.sql("""
                SELECT request_hash, response_status, response_body
                FROM idempotency_keys
                WHERE idempotency_key = :key
                """)
                .param("key", idempotencyKey)
                .query(StoredResponse.class)
                .optional()
                .map(stored -> {
                    if (!stored.requestHash().equals(requestHash)) {
                        throw new IdempotencyConflictException(idempotencyKey);
                    }
                    return stored;
                });
    }

    /**
     * Persists the response for a successfully processed request. Must be called in the same
     * transaction as the business logic.
     *
     * <p>If a concurrent duplicate INSERT wins, this call will throw a {@link DuplicateKeyException}
     * which the caller should handle by re-reading the stored response.
     */
    public void storeResponse(String idempotencyKey, String requestHash, int status, String body) {
        try {
            jdbc.sql("""
                    INSERT INTO idempotency_keys (idempotency_key, request_hash, response_status, response_body)
                    VALUES (:key, :hash, :status, :body)
                    """)
                    .param("key", idempotencyKey)
                    .param("hash", requestHash)
                    .param("status", status)
                    .param("body", body)
                    .update();
        } catch (DuplicateKeyException ignored) {
            // Another concurrent request won the race — it stored its response first.
            // The caller will return the original response from findExisting on the next call.
        }
    }

    /** Projection for the idempotency_keys SELECT result. */
    public record StoredResponse(String requestHash, int responseStatus, String responseBody) {}
}
