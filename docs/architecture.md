# System Architecture

## Component view

```mermaid
flowchart TB
    subgraph consumers["API consumers"]
        C1["Free tier client<br/>2 req/s · 100 req/month"]
        C2["Pro tier client<br/>10 req/s · 100k req/month"]
    end

    subgraph gateway["API Monetization Gateway"]
        direction TB
        F["MonetizationGatewayFilter<br/><i>servlet filter, runs before the DispatcherServlet</i>"]

        subgraph pipeline["Request pipeline"]
            direction LR
            A["1 · Authenticate<br/>CredentialResolver"]
            R["2 · Rate limit<br/>TokenBucketRateLimiter"]
            Q["3 · Quota<br/>QuotaService"]
            P["4 · Proxy"]
            M["5 · Meter<br/>UsageTrackingService"]
            A --> R --> Q --> P --> M
        end

        F --> pipeline
    end

    subgraph control["Control plane (not metered)"]
        TA["Tier admin API<br/><i>quota · rate · pricing</i>"]
        CA["Customer admin API<br/><i>onboarding · keys · plan changes</i>"]
        BA["Billing admin API<br/><i>manual re-runs · statements</i>"]
    end

    subgraph internal["Internal services"]
        S1["Business API<br/>/api/v1/**"]
    end

    subgraph jobs["Scheduled jobs"]
        J["MonthlyUsageAggregationJob<br/><i>cron: 1st of month, 02:15</i>"]
    end

    subgraph data["Persistence"]
        DB[("Relational store<br/>tiers · customers · keys<br/>quota counters · usage events<br/>monthly summaries")]
    end

    C1 & C2 -->|"X-API-Key"| F
    P --> S1
    A -.->|"read, cached ~30s"| DB
    Q <-->|"atomic conditional UPDATE"| DB
    M -->|"async write"| DB
    TA & CA -->|"write + invalidate cache"| DB
    J -->|"read events, write priced summaries"| DB
    BA --> J
    DB -.->|"statements"| BA
```

## Request sequence

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Filter as Gateway filter
    participant Auth as CredentialResolver
    participant RL as TokenBucketRateLimiter
    participant Quota as QuotaService
    participant Svc as Internal service
    participant Usage as UsageTrackingService
    participant DB as Database

    Client->>Filter: GET /api/v1/weather (X-API-Key)

    Filter->>Auth: resolve(key)
    alt cache hit
        Auth-->>Filter: customer + user + tier
    else cache miss
        Auth->>DB: SHA-256 lookup + subscription + tier
        DB-->>Auth: binding
        Auth-->>Filter: customer + user + tier
    end

    Filter->>RL: tryAcquire(customer, tier policy)
    alt no permit
        RL-->>Filter: denied (retry in N ms)
        Filter-->>Client: 429 RATE_LIMIT_EXCEEDED + Retry-After
    else permit granted
        RL-->>Filter: allowed (remaining)

        Filter->>Quota: tryConsume(customer, tier)
        Quota->>DB: UPDATE quota_counter SET used = used + 1<br/>WHERE used &lt; :limit
        alt 0 rows affected and no overage on the tier
            DB-->>Quota: quota exhausted
            Filter-->>Client: 429 QUOTA_EXCEEDED
        else consumed
            DB-->>Quota: consumed
            Filter->>Svc: forward request
            Svc-->>Filter: 200 payload
            Filter->>Usage: record(customer, user, endpoint, timestamp, ...)
            Usage-)DB: INSERT usage_event (off the request thread)
            Filter-->>Client: 200 + X-RateLimit-* / X-Quota-* headers
        end
    end
```

## Monthly billing pipeline

```mermaid
flowchart LR
    UE[("usage_event<br/><i>one row per served call</i>")]
    J["MonthlyUsageAggregationJob"]
    TIER[("tier<br/><i>quota + pricing</i>")]
    BS["BillingService<br/><i>pure pricing function</i>"]
    MS[("monthly_usage_summary")]
    ES[("endpoint_usage_summary")]

    UE -->|"GROUP BY endpoint, method"| J
    TIER --> BS
    J --> BS
    BS -->|"base + overage charge"| MS
    J -->|"per-route breakdown"| ES
    MS --- ES
```

The job groups in the database rather than in memory, so its footprint is independent of how many
events the month produced. Re-running a period replaces that period's summaries instead of adding to
them, which is what makes a retry after a partial failure safe.
