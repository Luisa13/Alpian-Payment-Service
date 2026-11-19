package ch.alpian.payment.api;

import ch.alpian.payment.api.generated.PaymentsApi;
import ch.alpian.payment.api.generated.model.CreatePaymentRequest;
import ch.alpian.payment.api.generated.model.PaymentResponse;
import ch.alpian.payment.application.flows.CreatePaymentFlow;
import ch.alpian.payment.application.flows.GetPaymentFlow;
import ch.alpian.payment.application.flows.PaymentResult;
import ch.alpian.payment.domain.Payment;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController implements PaymentsApi {

    private final CreatePaymentFlow createPaymentFlow;
    private final GetPaymentFlow getPaymentFlow;

    public PaymentController(CreatePaymentFlow createPaymentFlow, GetPaymentFlow getPaymentFlow) {
        this.createPaymentFlow = createPaymentFlow;
        this.getPaymentFlow = getPaymentFlow;
    }

    @Override
    public ResponseEntity<PaymentResponse> createPayment(
            String idempotencyKey, CreatePaymentRequest createPaymentRequest, String xRequestId) {

        CreatePaymentFlow.Arguments args = CreatePaymentRequestMapper.toArguments(idempotencyKey, createPaymentRequest);
        PaymentResult result = createPaymentFlow.initiate(args);
        Payment payment = result.payment();
        PaymentResponse body = ApiMapper.toResponse(payment);

        return switch (result.kind()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(body);
            case ACCEPTED ->
                ResponseEntity.accepted().location(locationOf(payment.getId())).body(body);
            case REPLAYED -> ResponseEntity.ok(body);
            case REJECTED -> throw new PaymentRejectedException(payment.getId(), payment.getFailureReason());
        };
    }

    @Override
    public ResponseEntity<PaymentResponse> getPayment(UUID id, String xRequestId) {
        Payment payment = getPaymentFlow.initiate(new GetPaymentFlow.Arguments(id));
        return ResponseEntity.ok(ApiMapper.toResponse(payment));
    }

    private static URI locationOf(UUID paymentId) {
        return URI.create(PaymentsApi.PATH_GET_PAYMENT.replace("{id}", paymentId.toString()));
    }
}
