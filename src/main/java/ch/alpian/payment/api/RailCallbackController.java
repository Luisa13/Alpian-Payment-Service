package ch.alpian.payment.api;

import ch.alpian.payment.api.generated.InternalApi;
import ch.alpian.payment.api.generated.model.RailCallbackRequest;
import ch.alpian.payment.application.flows.ProcessRailCallbackFlow;
import ch.alpian.payment.application.support.HmacSignature;
import ch.alpian.payment.application.support.RailCallbackProperties;
import ch.alpian.payment.application.support.TransactionRetry;
import ch.alpian.payment.domain.SettlementOutcome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal callback endpoint for asynchronous settlement outcomes from external rails.
 *
 * <p>Secured by HMAC-SHA256 signature verification ({@code X-Rail-Signature}). A production
 * deployment should additionally enforce mTLS or API-gateway authentication in front of this route.
 */
@RestController
public class RailCallbackController implements InternalApi {

    private final ProcessRailCallbackFlow processCallback;
    private final RailCallbackProperties properties;
    private final TransactionRetry retry;

    public RailCallbackController(
            ProcessRailCallbackFlow processCallback, RailCallbackProperties properties, TransactionRetry retry) {
        this.processCallback = processCallback;
        this.properties = properties;
        this.retry = retry;
    }

    @Override
    public ResponseEntity<Void> handleRailCallback(
            String xRailSignature, RailCallbackRequest request, String xRequestId) {
        String outcomeName = request.getOutcome().name();
        if (!HmacSignature.verify(
                properties.hmacSecret(),
                xRailSignature,
                request.getEventId(),
                request.getPaymentId(),
                outcomeName,
                request.getRailReference())) {
            throw new InvalidSignatureException();
        }

        SettlementOutcome outcome = request.getOutcome() == RailCallbackRequest.OutcomeEnum.SETTLED
                ? SettlementOutcome.settled(request.getRailReference())
                : SettlementOutcome.rejected();

        ProcessRailCallbackFlow.Result result =
                retry.execute(() -> processCallback.process(request.getEventId(), request.getPaymentId(), outcome));

        return switch (result) {
            case APPLIED -> ResponseEntity.noContent().build();
            case REPLAYED -> ResponseEntity.ok().build();
        };
    }
}
