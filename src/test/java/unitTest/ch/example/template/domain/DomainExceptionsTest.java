package ch.example.template.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionsTest {

    @Test
    void entityNotFound_message_contains_type_and_id() {
        var ex = new EntityNotFoundException("Payment", "abc-123");
        assertThat(ex.getMessage()).contains("Payment").contains("abc-123");
    }

    @Test
    void idempotencyConflict_message_contains_key() {
        var ex = new IdempotencyConflictException("my-key");
        assertThat(ex.getMessage()).contains("my-key");
    }
}
