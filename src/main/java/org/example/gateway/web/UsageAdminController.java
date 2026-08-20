package org.example.gateway.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.example.gateway.domain.UsageEvent;
import org.example.gateway.quota.QuotaService;
import org.example.gateway.repository.UsageEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read access to the raw usage event stream, for support and debugging. */
@RestController
@RequestMapping("/admin/usage")
public class UsageAdminController {

    private final UsageEventRepository usageEventRepository;
    private final QuotaService quotaService;

    public UsageAdminController(UsageEventRepository usageEventRepository, QuotaService quotaService) {
        this.usageEventRepository = usageEventRepository;
        this.quotaService = quotaService;
    }

    @GetMapping("/{customerId}")
    public List<Map<String, Object>> events(@PathVariable Long customerId,
                                            @RequestParam(required = false) String period) {
        String resolved = period == null ? quotaService.currentPeriod() : period;
        return usageEventRepository.findByCustomerIdAndBillingPeriod(customerId, resolved).stream()
                .map(UsageAdminController::toMap)
                .toList();
    }

    private static Map<String, Object> toMap(UsageEvent event) {
        return Map.of(
                "customerId", event.getCustomerId(),
                "userId", event.getUserId(),
                "endpoint", event.getEndpoint(),
                "method", event.getHttpMethod(),
                "status", event.getStatusCode(),
                "timestamp", event.getOccurredAt().toString(),
                "latencyMs", event.getLatencyMs(),
                "tier", event.getTierCode(),
                "billable", event.isBillable(),
                "billingPeriod", event.getBillingPeriod());
    }

    @GetMapping("/{customerId}/count")
    public Map<String, Object> count(@PathVariable Long customerId,
                                     @RequestParam(required = false) String period) {
        String resolved = period == null ? quotaService.currentPeriod() : period;
        return Map.of(
                "customerId", customerId,
                "billingPeriod", resolved,
                "events", usageEventRepository.countByCustomerIdAndBillingPeriod(customerId, resolved),
                "asOf", Instant.now().toString());
    }
}
