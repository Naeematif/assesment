package org.example.gateway.error;

/** Carries an {@link ErrorCode} plus optional structured details out to the error writer. */
public class GatewayException extends RuntimeException {

    private final ErrorCode code;
    private final transient java.util.Map<String, Object> details;

    public GatewayException(ErrorCode code) {
        this(code, code.defaultMessage(), java.util.Map.of());
    }

    public GatewayException(ErrorCode code, String message) {
        this(code, message, java.util.Map.of());
    }

    public GatewayException(ErrorCode code, String message, java.util.Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode getCode() {
        return code;
    }

    public java.util.Map<String, Object> getDetails() {
        return details;
    }
}
