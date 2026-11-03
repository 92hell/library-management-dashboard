package com.helmifr.library.common;

/**
 * Phone numbers are typed in many formats ("0812-3456-7890", "+62 812 3456 7890"). Two numbers
 * are considered the same when, ignoring punctuation and leading zeros, one ends with the other
 * and at least 8 digits match - which makes local and international forms equal.
 */
public final class PhoneNumbers {
    /** Accepted input: optional +, then digits with spaces, dashes or brackets (e.g. "+62 812-3456-7890"). */
    public static final String PATTERN = "^\\+?[0-9][0-9 ()-]{4,24}$";
    public static final String PATTERN_MESSAGE = "must be a phone number: digits, spaces or dashes, optionally starting with +";

    private static final int MIN_MATCHING_DIGITS = 8;

    private PhoneNumbers() {}

    public static boolean same(String a, String b) {
        String x = significantDigits(a);
        String y = significantDigits(b);
        if (x.length() < MIN_MATCHING_DIGITS || y.length() < MIN_MATCHING_DIGITS) {
            return false;
        }
        return x.endsWith(y) || y.endsWith(x);
    }

    /** The last digits, for narrowing database lookups before comparing with {@link #same}. */
    public static String lastDigits(String phone, int count) {
        String digits = significantDigits(phone);
        return digits.length() <= count ? digits : digits.substring(digits.length() - count);
    }

    /** Storage form: digits, with a leading + kept for international numbers. */
    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String trimmed = phone.trim();
        return (trimmed.startsWith("+") ? "+" : "") + trimmed.replaceAll("\\D", "");
    }

    /** For logs: only the last 4 digits stay visible. */
    public static String mask(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    private static String significantDigits(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "").replaceFirst("^0+", "");
    }
}
