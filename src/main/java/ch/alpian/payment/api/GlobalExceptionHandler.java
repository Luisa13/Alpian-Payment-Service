package ch.alpian.payment.api;

import ch.alpian.payment.domain.exception.CurrencyMismatchException;
import ch.alpian.payment.domain.exception.EntityNotFoundException;
import ch.alpian.payment.domain.exception.IdempotencyConflictException;
import ch.alpian.payment.domain.exception.UnknownAccountException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every failure to an RFC 9457 {@link ProblemDetail} with a stable {@code code} property
 * (the {@code ProblemDetail.code} enum of the OpenAPI spec).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://example.com/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            violations.put(fe.getField(), fe.getDefaultMessage());
        }
        return validationProblem(violations, request);
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ProblemDetail> handleRequestValidation(RequestValidationException ex, WebRequest request) {
        return validationProblem(ex.getViolations(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> violations.put(lastSegment(v.getPropertyPath().toString()), v.getMessage()));
        return validationProblem(violations, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException ex, WebRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors()
                    .forEach(e -> violations.put(name == null ? "parameter" : name, e.getDefaultMessage()));
        });
        return validationProblem(violations, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        return validationProblem(Map.of(ex.getName(), "has an invalid value"), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, WebRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Malformed Request",
                "Request body is missing or malformed",
                request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex, WebRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "MISSING_HEADER",
                "Missing Header",
                "Required header '" + ex.getHeaderName() + "' is missing",
                request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException ex, WebRequest request) {
        String code = "Payment".equals(ex.getEntityType()) ? "PAYMENT_NOT_FOUND" : "ACCOUNT_NOT_FOUND";
        return problem(HttpStatus.NOT_FOUND, code, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(
            IdempotencyConflictException ex, WebRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "IDEMPOTENCY_KEY_REUSED",
                "Idempotency Key Reused",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(UnknownAccountException.class)
    public ResponseEntity<ProblemDetail> handleUnknownAccount(UnknownAccountException ex, WebRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_ACCOUNT", "Unknown Account", ex.getMessage(), request);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ProblemDetail> handleCurrencyMismatch(CurrencyMismatchException ex, WebRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH", "Currency Mismatch", ex.getMessage(), request);
    }

    @ExceptionHandler(PaymentRejectedException.class)
    public ResponseEntity<ProblemDetail> handleRejected(PaymentRejectedException ex, WebRequest request) {
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getReason().name(),
                "Payment Rejected",
                "The payment was rejected: " + ex.getReason(),
                request);
        response.getBody().setProperty("paymentId", ex.getPaymentId());
        return response;
    }

    /** Deadlock or lock timeout that outlasted the retries, or a stale update. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrencyFailure(ConcurrencyFailureException ex, WebRequest request) {
        log.warn("Concurrency failure on {}: {}", requestUri(request), ex.toString());
        return problem(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "Concurrent Modification",
                "The request collided with another one; please retry",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, WebRequest request) {
        String errorRef = UUID.randomUUID().toString();
        log.error("Unexpected error [errorRef={}] on {}", errorRef, requestUri(request), ex);
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Internal Server Error",
                "An unexpected error occurred; please contact support",
                request);
        response.getBody().setProperty("errorReference", errorRef);
        return response;
    }

    private ResponseEntity<ProblemDetail> validationProblem(Map<String, String> violations, WebRequest request) {
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation Failed",
                "Request has " + violations.size() + " validation error(s)",
                request);
        response.getBody().setProperty("violations", violations);
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String title, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase().replace('_', '-')));
        problem.setInstance(URI.create(requestUri(request)));
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private static String requestUri(WebRequest request) {
        String desc = request.getDescription(false);
        return desc.startsWith("uri=") ? desc.substring(4) : desc;
    }
}
