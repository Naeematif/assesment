package org.example.gateway.ratelimit;

/**
 * Monotonic nanosecond clock.
 *
 * <p>Indirecting time behind an interface is what makes the token bucket testable: unit tests can
 * advance a fake ticker by exactly one second instead of sleeping and hoping.
 */
@FunctionalInterface
public interface Ticker {

    long nanos();

    Ticker SYSTEM = System::nanoTime;
}
