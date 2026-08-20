package org.example.gateway.ratelimit;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** Controllable clock so rate limit behaviour can be asserted exactly instead of slept for. */
public class FakeTicker implements Ticker {

    private final AtomicLong nanos = new AtomicLong(0);

    @Override
    public long nanos() {
        return nanos.get();
    }

    public void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    public void advanceMillis(long millis) {
        nanos.addAndGet(millis * 1_000_000L);
    }
}
