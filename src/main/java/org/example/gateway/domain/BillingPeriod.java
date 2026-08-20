package org.example.gateway.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Helper for the calendar-month billing window.
 *
 * <p>Periods are stored as {@code YYYY-MM} strings so they sort lexicographically and are trivially
 * greppable in logs and reports. All conversions go through an explicit zone: the billing month for
 * a request at 23:30 on the 31st depends entirely on which timezone you ask in.
 */
public final class BillingPeriod {

    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private BillingPeriod() {
    }

    public static String of(Instant instant, ZoneId zone) {
        return YearMonth.from(instant.atZone(zone)).format(FORMAT);
    }

    public static String of(YearMonth yearMonth) {
        return yearMonth.format(FORMAT);
    }

    public static YearMonth parse(String period) {
        return YearMonth.parse(period, FORMAT);
    }

    /** Inclusive start of the period. */
    public static Instant startOf(String period, ZoneId zone) {
        return parse(period).atDay(1).atStartOfDay(zone).toInstant();
    }

    /** Exclusive end of the period. */
    public static Instant endOf(String period, ZoneId zone) {
        return parse(period).plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
    }
}
