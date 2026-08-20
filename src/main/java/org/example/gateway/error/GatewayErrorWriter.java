package org.example.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Serialises errors raised inside the servlet filter chain.
 *
 * <p>{@code @RestControllerAdvice} cannot help here: the gateway rejects requests before the
 * DispatcherServlet ever sees them, which is the whole point - an over-quota call must not reach the
 * business logic. So the filter writes the same error shape itself.
 */
@Component
public class GatewayErrorWriter {

    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, GatewayException exception)
            throws IOException {
        write(request, response, exception.getCode(), exception.getMessage(), exception.getDetails());
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
                      String message, Map<String, Object> details) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(code, message, request.getRequestURI(), details);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
