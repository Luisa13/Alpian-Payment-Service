package ch.alpian.payment.application.flows;

import ch.alpian.payment.application.tasks.FindAccountByIdTask;
import ch.alpian.payment.domain.Account;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Flow: retrieve an account by id. */
@Service
public class GetAccountFlow {

    private final FindAccountByIdTask findAccountById;

    public record Arguments(UUID accountId) {}

    public GetAccountFlow(FindAccountByIdTask findAccountById) {
        this.findAccountById = findAccountById;
    }

    public Account initiate(Arguments args) {
        return findAccountById.execute(args.accountId());
    }
}
