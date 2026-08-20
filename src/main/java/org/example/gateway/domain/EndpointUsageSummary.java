package org.example.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Per-endpoint breakdown of a {@link MonthlyUsageSummary}. */
@Entity
@Table(name = "endpoint_usage_summary")
public class EndpointUsageSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "summary_id", nullable = false)
    private MonthlyUsageSummary summary;

    @Column(name = "endpoint", nullable = false, length = 300)
    private String endpoint;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "avg_latency_ms", nullable = false)
    private long avgLatencyMs;

    protected EndpointUsageSummary() {
        // for JPA
    }

    public EndpointUsageSummary(String endpoint, String httpMethod, long requestCount, long avgLatencyMs) {
        this.endpoint = endpoint;
        this.httpMethod = httpMethod;
        this.requestCount = requestCount;
        this.avgLatencyMs = avgLatencyMs;
    }

    public Long getId() {
        return id;
    }

    public MonthlyUsageSummary getSummary() {
        return summary;
    }

    void setSummary(MonthlyUsageSummary summary) {
        this.summary = summary;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public long getAvgLatencyMs() {
        return avgLatencyMs;
    }
}
