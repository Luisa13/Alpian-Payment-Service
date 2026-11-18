package ch.example.template.infrastructure.persistence;

import ch.example.template.domain.IdempotencyConflictException;
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
 * <p>Protocol (called from application services):
 *
 * <ol>
 *   <li>Hash the raw request body with SHA-256.
 *   <li>Call {@link #findExisting} inside the same transaction as the business logic.
 *   <li>If present with same hash → replay. Different hash → throws {@link
 *       IdempotencyConflictException}. Empty → proceed, then call {@link #storeResponse}.
 * </ol>
 */
@Component
public class IdempotencyStore {

    private final JdbcClient jdbc;

    public IdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public static String hash(String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

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
        }
    }

    public record StoredResponse(String requestHash, int responseStatus, String responseBody) {}
}
