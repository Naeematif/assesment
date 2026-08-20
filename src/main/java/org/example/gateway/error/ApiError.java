package org.example.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Uniform error body used by both the gateway filter and the Spring MVC controllers. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(Instant timestamp,
                       int status,
                       String error,
                       String code,
                       String message,
                       String path,
                       Map<String, Object> details) {

    public static ApiError of(ErrorCode code, String message, String path, Map<String, Object> details) {
        return new ApiError(Instant.now(), code.status().value(), code.status().getReasonPhrase(),
                code.name(), message, path, details == null ? Map.of() : details);
    }
}
