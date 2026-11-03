package com.onlinestore.cart.mysql;

import java.util.OptionalLong;

/**
 * Parses whole numbers from URL path segments.
 *
 * <p>Only an optional {@code +}/{@code -} sign followed by ASCII digits is accepted, and the
 * value must fit in a 64-bit long. Anything else (spaces, decimals, non-ASCII digits) is rejected.
 */
public final class ParamParser {

    private ParamParser() {
    }

    public static OptionalLong parseLong(String text) {
        if (text == null || text.isEmpty()) {
            return OptionalLong.empty();
        }
        int start = (text.charAt(0) == '+' || text.charAt(0) == '-') ? 1 : 0;
        if (start == text.length()) {
            return OptionalLong.empty();
        }
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return OptionalLong.empty();
            }
        }
        try {
            return OptionalLong.of(Long.parseLong(text));
        } catch (NumberFormatException overflow) {
            return OptionalLong.empty();
        }
    }
}
