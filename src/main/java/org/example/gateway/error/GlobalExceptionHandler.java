package org.example.gateway.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps exceptions thrown by the admin and business controllers onto the shared error body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiError> handleGateway(GatewayException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.getCode(), ex.getMessage(), request.getRequestURI(), ex.getDetails());
        return ResponseEntity.status(ex.getCode().status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        ApiError body = ApiError.of(ErrorCode.VALIDATION_FAILED, "Request is not valid",
                request.getRequestURI(), fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        ApiError body = ApiError.of(ErrorCode.VALIDATION_FAILED, ex.getMessage(), request.getRequestURI(), Map.of());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ApiError body = ApiError.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(),
                request.getRequestURI(), Map.of());
        return ResponseEntity.internalServerError().body(body);
    }
}
