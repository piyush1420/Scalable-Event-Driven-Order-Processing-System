package com.onlinestore.product;

import java.util.Locale;

/**
 * Formats a duration in the shortest readable unit, e.g. {@code 950ns}, {@code 617.958µs},
 * {@code 1.23775ms}, {@code 2.0113s} or {@code 1h2m3.5s}. Trailing zeros are dropped.
 */
public final class CompactDuration {

    private static final long MICRO = 1_000L;
    private static final long MILLI = 1_000_000L;
    private static final long SECOND = 1_000_000_000L;

    private CompactDuration() {
    }

    public static String format(long nanos) {
        if (nanos == 0) {
            return "0s";
        }
        String sign = nanos < 0 ? "-" : "";
        long u = Math.abs(nanos);

        if (u < SECOND) {
            if (u < MICRO) {
                return sign + u + "ns";
            }
            if (u < MILLI) {
                return sign + withFraction(u, 3) + "µs";
            }
            return sign + withFraction(u, 6) + "ms";
        }

        long totalSeconds = u / SECOND;
        String text = (totalSeconds % 60) + fraction(u % SECOND, 9) + "s";
        long minutes = totalSeconds / 60;
        if (minutes > 0) {
            text = (minutes % 60) + "m" + text;
            long hours = minutes / 60;
            if (hours > 0) {
                text = hours + "h" + text;
            }
        }
        return sign + text;
    }

    /** {@code value} has {@code digits} implied decimal places, e.g. (1237750, 6) -> "1.23775". */
    private static String withFraction(long value, int digits) {
        long scale = (long) Math.pow(10, digits);
        return (value / scale) + fraction(value % scale, digits);
    }

    private static String fraction(long remainder, int digits) {
        if (remainder == 0) {
            return "";
        }
        String padded = String.format(Locale.ROOT, "%0" + digits + "d", remainder);
        int end = padded.length();
        while (padded.charAt(end - 1) == '0') {
            end--;
        }
        return "." + padded.substring(0, end);
    }
}
