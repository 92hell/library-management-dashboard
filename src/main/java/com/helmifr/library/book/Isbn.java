package com.helmifr.library.book;

import java.util.Optional;

/** ISBN parsing: accepts ISBN-10 or ISBN-13 with or without hyphens/spaces; normalises to ISBN-13 digits. */
public final class Isbn {
    private Isbn() {}

    /** The ISBN-13 for a valid ISBN-10/13, or empty if the input is not a valid ISBN. */
    public static Optional<String> toIsbn13(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String s = input.replaceAll("[\\s-]", "").toUpperCase();
        if (s.matches("\\d{9}[\\dX]") && validIsbn10(s)) {
            String core = "978" + s.substring(0, 9);
            return Optional.of(core + isbn13CheckDigit(core));
        }
        if (s.matches("97[89]\\d{10}") && isbn13CheckDigit(s.substring(0, 12)) == s.charAt(12) - '0') {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    private static boolean validIsbn10(String s) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            int digit = s.charAt(i) == 'X' ? 10 : s.charAt(i) - '0';
            if (digit == 10 && i != 9) {
                return false;
            }
            sum += digit * (10 - i);
        }
        return sum % 11 == 0;
    }

    private static int isbn13CheckDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (first12.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - sum % 10) % 10;
    }
}
