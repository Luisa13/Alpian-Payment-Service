package ch.example.template.shared;

import static org.assertj.core.api.Assertions.assertThat;

import ch.example.template.shared.application.IdempotencyConflictException;
import ch.example.template.shared.domain.EntityNotFoundException;
import org.junit.jupiter.api.Test;

class SharedExceptionsTest {

    @Test
    void entityNotFound_message_contains_type_and_id() {
        var ex = new EntityNotFoundException("Transfer", "abc-123");
        assertThat(ex.getMessage()).contains("Transfer").contains("abc-123");
    }

    @Test
    void idempotencyConflict_message_contains_key() {
        var ex = new IdempotencyConflictException("my-key");
        assertThat(ex.getMessage()).contains("my-key");
    }
}
