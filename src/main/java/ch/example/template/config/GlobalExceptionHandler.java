package ch.example.template.config;

import ch.example.template.shared.application.IdempotencyConflictException;
import ch.example.template.shared.domain.EntityNotFoundException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
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

/**
 * Global exception handler mapping domain and application exceptions to RFC 9457 {@link
 * ProblemDetail} responses.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>No stack traces are ever sent to the client.
 *   <li>5xx responses include an opaque {@code errorReference} UUID for log correlation.
 *   <li>400 responses include a {@code violations} map of field → message for validation errors.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            violations.put(fe.getField(), fe.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body has " + violations.size() + " validation error(s)");
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://example.com/problems/validation-error"));
        problem.setInstance(URI.create(requestUri(request)));
        problem.setProperty("violations", violations);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, WebRequest request) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
        problem.setTitle("Malformed Request");
        problem.setType(URI.create("https://example.com/problems/malformed-request"));
        problem.setInstance(URI.create(requestUri(request)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Required header '" + ex.getHeaderName() + "' is missing");
        problem.setTitle("Missing Header");
        problem.setType(URI.create("https://example.com/problems/missing-header"));
        problem.setInstance(URI.create(requestUri(request)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        problem.setType(URI.create("https://example.com/problems/not-found"));
        problem.setInstance(URI.create(requestUri(request)));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(
            IdempotencyConflictException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Idempotency Conflict");
        problem.setType(URI.create("https://example.com/problems/idempotency-conflict"));
        problem.setInstance(URI.create(requestUri(request)));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockingFailureException ex, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The resource was modified by another request; please retry");
        problem.setTitle("Concurrent Modification");
        problem.setType(URI.create("https://example.com/problems/concurrent-modification"));
        problem.setInstance(URI.create(requestUri(request)));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, WebRequest request) {

        String errorRef = UUID.randomUUID().toString();
        // Log the full exception server-side; never expose the stack trace to the client
        log.error("Unexpected error [errorRef={}] on {}", errorRef, requestUri(request), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred; please contact support");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://example.com/problems/internal-error"));
        problem.setInstance(URI.create(requestUri(request)));
        problem.setProperty("errorReference", errorRef);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String requestUri(WebRequest request) {
        // Strip query string to avoid leaking sensitive parameters into ProblemDetail
        String desc = request.getDescription(false);
        // desc format is "uri=/v1/transfers"
        return desc.startsWith("uri=") ? desc.substring(4) : desc;
    }
}
