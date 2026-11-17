package ch.example.template.transfer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.example.template.shared.application.IdempotencyConflictException;
import ch.example.template.shared.domain.EntityNotFoundException;
import ch.example.template.transfer.application.TransferService;
import ch.example.template.transfer.domain.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice test for {@link TransferController}.
 *
 * <p>No infrastructure (DB, Kafka) — only the HTTP contract is tested here.
 */
@WebMvcTest(TransferController.class)
class TransferControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TransferService transferService;

    private static final String VALID_BODY = """
            {
              "amount": "100.00",
              "currency": "CHF",
              "sourceAccountId": "a0000000-0000-0000-0000-000000000001",
              "targetAccountId": "a0000000-0000-0000-0000-000000000002",
              "description": "Rent payment"
            }
            """;

    // ── POST /v1/transfers ────────────────────────────────────────────────────

    @Test
    void post_valid_request_returns_201() throws Exception {
        var transfer = stubTransfer();
        when(transferService.create(any(), anyString(), anyString()))
                .thenReturn(TransferService.TransferResult.created(transfer));

        mvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void post_missing_idempotency_key_returns_400() throws Exception {
        mvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_missing_required_fields_returns_400_with_violations() throws Exception {
        String invalidBody = """
                { "description": "missing required fields" }
                """;
        mvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void post_idempotent_replay_returns_201_same_body() throws Exception {
        var stored = new TransferService.TransferResult.Replayed(201, "{\"id\":\"" + UUID.randomUUID() + "\"}");
        when(transferService.create(any(), anyString(), anyString())).thenReturn(stored);

        mvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void post_idempotency_conflict_returns_409() throws Exception {
        when(transferService.create(any(), anyString(), anyString()))
                .thenThrow(new IdempotencyConflictException("key-1"));

        mvc.perform(post("/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    // ── GET /v1/transfers/{id} ────────────────────────────────────────────────

    @Test
    void get_existing_transfer_returns_200() throws Exception {
        var transfer = stubTransfer();
        when(transferService.findById(transfer.getId())).thenReturn(transfer);

        mvc.perform(get("/v1/transfers/" + transfer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transfer.getId().toString()));
    }

    @Test
    void get_unknown_transfer_returns_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(transferService.findById(id)).thenThrow(new EntityNotFoundException("Transfer", id));

        mvc.perform(get("/v1/transfers/" + id)).andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transfer stubTransfer() {
        return Transfer.create(
                new ch.example.template.shared.domain.Money(new BigDecimal("100.00"), "CHF"),
                UUID.fromString("a0000000-0000-0000-0000-000000000001"),
                UUID.fromString("a0000000-0000-0000-0000-000000000002"),
                "Rent payment");
    }
}
