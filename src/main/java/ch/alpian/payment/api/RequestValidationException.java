package ch.alpian.payment.api;

import java.util.Map;

/** The request is well-formed JSON but breaks a rule Bean Validation cannot express. */
public class RequestValidationException extends RuntimeException {

    private final Map<String, String> violations;

    public RequestValidationException(Map<String, String> violations) {
        super("Request has " + violations.size() + " validation error(s)");
        this.violations = Map.copyOf(violations);
    }

    public Map<String, String> getViolations() {
        return violations;
    }
}
