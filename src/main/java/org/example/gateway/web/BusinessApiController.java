package org.example.gateway.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.example.gateway.gateway.GatewayHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stand-in for the internal services the gateway fronts.
 *
 * <p>Everything under the metered prefix goes through the full monetization pipeline, so these
 * handlers are only ever reached by an authenticated caller that is inside both its rate limit and
 * its monthly quota. They deliberately contain no auth or metering logic of their own - that
 * separation is the point of the gateway.
 *
 * <p>In a real deployment these routes would proxy to upstream services instead of being served
 * in-process; the filter would be unchanged.
 */
@RestController
@RequestMapping("/api/v1")
public class BusinessApiController {

    @GetMapping("/weather")
    public Map<String, Object> weather(HttpServletRequest request) {
        return Map.of(
                "city", "Amsterdam",
                "temperatureC", 18,
                "conditions", "Partly cloudy",
                "servedAt", Instant.now().toString(),
                "servedTo", caller(request));
    }

    @GetMapping("/products")
    public List<Map<String, Object>> products() {
        return List.of(
                Map.of("id", 1, "name", "Starter widget", "priceUsd", 9.99),
                Map.of("id", 2, "name", "Industrial widget", "priceUsd", 149.00));
    }

    /** Path variable rather than a literal, to show usage rolling up by route template. */
    @GetMapping("/products/{id}")
    public Map<String, Object> product(@PathVariable Long id) {
        return Map.of("id", id, "name", "Widget " + id, "priceUsd", 9.99 * id);
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody(required = false) Map<String, Object> body,
                                    HttpServletRequest request) {
        return Map.of(
                "received", body == null ? Map.of() : body,
                "servedTo", caller(request),
                "servedAt", Instant.now().toString());
    }

    /** The gateway publishes the authenticated identity as request attributes. */
    private Map<String, Object> caller(HttpServletRequest request) {
        return Map.of(
                "customerId", String.valueOf(request.getAttribute(GatewayHeaders.ATTR_CUSTOMER_ID)),
                "userId", String.valueOf(request.getAttribute(GatewayHeaders.ATTR_USER_ID)),
                "tier", String.valueOf(request.getAttribute(GatewayHeaders.ATTR_TIER)));
    }
}
