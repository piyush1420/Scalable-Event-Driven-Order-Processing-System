package com.onlinestore.inventory;

import java.util.Locale;

/** The three behaviours the simulated inventory service can be switched between. */
public enum Mode {
    /** 100% success, no delay. */
    HEALTHY,
    /** 50% of requests fail after 3-10s, the rest succeed after 2-4s. */
    DEGRADED,
    /** Every request hangs for 10s and then fails. */
    CRASHED;

    /** Lower-case name used in URLs and JSON, e.g. {@code "healthy"}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Mode fromLabel(String label) {
        return valueOf(label.toUpperCase(Locale.ROOT));
    }
}
