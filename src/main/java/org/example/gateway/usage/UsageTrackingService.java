package org.example.gateway.usage;

import java.util.concurrent.Executor;
import org.example.gateway.domain.UsageEvent;
import org.example.gateway.repository.UsageEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Persists the raw usage event stream that billing is computed from.
 *
 * <p>Writes go through an injected {@link Executor} so that, in production, logging a call never
 * shows up in the caller's latency. The executor is configured with a bounded queue and a
 * caller-runs saturation policy: under extreme load the gateway would rather slow down than silently
 * drop events, because a dropped event is revenue that is never invoiced.
 *
 * <p>Tests wire a synchronous executor via {@code gateway.usage.async=false} to get deterministic
 * assertions without sleeping.
 */
@Service
public class UsageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(UsageTrackingService.class);

    private final UsageEventRepository repository;
    private final Executor executor;

    public UsageTrackingService(UsageEventRepository repository, Executor usageExecutor) {
        this.repository = repository;
        this.executor = usageExecutor;
    }

    public void record(UsageRecord record) {
        executor.execute(() -> {
            try {
                repository.save(toEntity(record));
            } catch (RuntimeException e) {
                // Never let a metering failure surface to the consumer; the call itself already
                // succeeded. Losing the event is logged loudly so it can be reconciled.
                log.error("Failed to persist usage event for customer {} endpoint {}",
                        record.customerId(), record.endpoint(), e);
            }
        });
    }

    private UsageEvent toEntity(UsageRecord r) {
        return new UsageEvent(r.customerId(), r.userId(), r.apiKeyId(), r.endpoint(), r.httpMethod(),
                r.statusCode(), r.occurredAt(), r.latencyMs(), r.billingPeriod(), r.tierCode(), r.billable());
    }
}
