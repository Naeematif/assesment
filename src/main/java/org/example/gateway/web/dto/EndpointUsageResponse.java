package org.example.gateway.web.dto;

import org.example.gateway.domain.EndpointUsageSummary;

public record EndpointUsageResponse(String endpoint,
                                    String httpMethod,
                                    long requestCount,
                                    long avgLatencyMs) {

    public static EndpointUsageResponse from(EndpointUsageSummary summary) {
        return new EndpointUsageResponse(summary.getEndpoint(), summary.getHttpMethod(),
                summary.getRequestCount(), summary.getAvgLatencyMs());
    }
}
