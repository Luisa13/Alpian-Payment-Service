package ch.alpian.payment.api;

import ch.alpian.payment.api.generated.AccountsApi;
import ch.alpian.payment.api.generated.model.AccountResponse;
import ch.alpian.payment.application.flows.GetAccountFlow;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController implements AccountsApi {

    private final GetAccountFlow getAccountFlow;

    public AccountController(GetAccountFlow getAccountFlow) {
        this.getAccountFlow = getAccountFlow;
    }

    @Override
    public ResponseEntity<AccountResponse> getAccount(UUID id, String xRequestId) {
        return ResponseEntity.ok(ApiMapper.toResponse(getAccountFlow.initiate(new GetAccountFlow.Arguments(id))));
    }
}
