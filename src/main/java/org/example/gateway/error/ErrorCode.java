package org.example.gateway.error;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable failure reasons returned to API consumers.
 *
 * <p>Rate limit and quota exhaustion share HTTP 429 but are very different problems for the caller -
 * one clears in a second, the other needs an upgrade - so they are separate codes. Clients should
 * branch on {@code code}, never on the human-readable message.
 */
public enum ErrorCode {

    MISSING_API_KEY(HttpStatus.UNAUTHORIZED, "API key is required"),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "API key is not valid"),
    API_KEY_REVOKED(HttpStatus.UNAUTHORIZED, "API key has been revoked"),
    CUSTOMER_SUSPENDED(HttpStatus.FORBIDDEN, "Customer account is suspended"),
    NO_ACTIVE_SUBSCRIPTION(HttpStatus.FORBIDDEN, "No active subscription for this account"),
    TIER_INACTIVE(HttpStatus.FORBIDDEN, "The subscribed tier is no longer available"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"),
    QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Monthly quota exhausted"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request is not valid"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(HttpStatus.CONFLICT, "Resource already exists"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
