package com.helmifr.library.common;

import java.util.Locale;

/**
 * Builds lower-cased "contains" LIKE patterns in which the user's %, _ and ! match literally.
 * Queries must use {@code ESCAPE '!'} ('!' rather than backslash, which MariaDB treats specially).
 */
public final class SearchPatterns {
    private SearchPatterns() {}

    /** Returns null for a blank term, meaning "no filter". */
    public static String contains(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String escaped = term.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
