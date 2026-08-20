# Entity Relationship Diagram

```mermaid
erDiagram
    TIER ||--o{ SUBSCRIPTION : "is sold as"
    CUSTOMER ||--|| SUBSCRIPTION : "has one"
    CUSTOMER ||--o{ API_KEY : "issues"
    CUSTOMER ||--o{ QUOTA_COUNTER : "consumes per month"
    CUSTOMER ||--o{ USAGE_EVENT : "generates"
    API_KEY ||--o{ USAGE_EVENT : "identifies"
    CUSTOMER ||--o{ MONTHLY_USAGE_SUMMARY : "is invoiced by"
    MONTHLY_USAGE_SUMMARY ||--o{ ENDPOINT_USAGE_SUMMARY : "breaks down into"

    TIER {
        bigint   id PK
        varchar  code UK "FREE, PRO, ..."
        varchar  display_name
        bigint   monthly_quota "requests per calendar month"
        int      rate_limit_per_second "sustained rate"
        int      burst_capacity "token bucket size"
        decimal  monthly_price "recurring fee"
        decimal  overage_price_per_request "charge beyond quota"
        boolean  overage_allowed "false = hard cap"
        boolean  active
        bigint   version "optimistic lock"
        instant  created_at
        instant  updated_at
    }

    CUSTOMER {
        bigint   id PK
        varchar  name
        varchar  email UK
        varchar  status "ACTIVE, SUSPENDED"
        instant  created_at
    }

    SUBSCRIPTION {
        bigint   id PK
        bigint   customer_id FK "unique - one active plan per account"
        bigint   tier_id FK
        varchar  status "ACTIVE, PAST_DUE, CANCELLED"
        instant  started_at
        instant  updated_at
    }

    API_KEY {
        bigint   id PK
        varchar  key_hash UK "SHA-256; plaintext is never stored"
        varchar  key_prefix "non-secret, for support"
        bigint   customer_id FK
        varchar  user_id "the individual key holder"
        varchar  label
        varchar  status "ACTIVE, REVOKED"
        instant  created_at
    }

    QUOTA_COUNTER {
        bigint   id PK
        bigint   customer_id FK
        varchar  billing_period "yyyy-MM"
        bigint   quota_limit "snapshot, for reporting"
        bigint   used "advanced by conditional UPDATE"
        instant  updated_at
    }

    USAGE_EVENT {
        bigint   id PK
        bigint   customer_id FK
        varchar  user_id
        bigint   api_key_id FK
        varchar  endpoint "route template, not raw URI"
        varchar  http_method
        int      status_code
        instant  occurred_at
        bigint   latency_ms
        varchar  billing_period "denormalised for the job"
        varchar  tier_code "tier in force at call time"
        boolean  billable "false for 5xx"
    }

    MONTHLY_USAGE_SUMMARY {
        bigint   id PK
        bigint   customer_id FK
        varchar  billing_period "yyyy-MM"
        varchar  tier_code
        bigint   total_requests
        bigint   included_requests
        bigint   overage_requests
        decimal  base_charge
        decimal  overage_charge
        decimal  total_charge
        varchar  currency
        instant  generated_at
    }

    ENDPOINT_USAGE_SUMMARY {
        bigint   id PK
        bigint   summary_id FK
        varchar  endpoint
        varchar  http_method
        bigint   request_count
        bigint   avg_latency_ms
    }
```

## Notes on the model

**`QUOTA_COUNTER` is separate from `USAGE_EVENT` on purpose.** Enforcement needs one number it can
read and advance atomically on every request; billing needs the full event history. Deriving the
live counter with `SELECT COUNT(*)` over the events would put a growing aggregate scan on the hot
path, and would still be racy without additional locking.

**`USAGE_EVENT` is deliberately denormalised.** It copies `billing_period` and `tier_code` rather
than joining to find them, so a customer who changes plan mid-month still has each call priced
against the tier that was in force when it was served, and the aggregation query touches one table.

**Uniqueness carries the concurrency guarantees.** `(customer_id, billing_period)` is unique on
`QUOTA_COUNTER`, which is what makes the lazy create-on-first-request safe, and
`(customer_id, billing_period)` is unique on `MONTHLY_USAGE_SUMMARY`, which is what makes re-running
the billing job idempotent rather than duplicative.
