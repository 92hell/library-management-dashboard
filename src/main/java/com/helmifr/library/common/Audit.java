package com.helmifr.library.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security and business events ("who did what"), written through the logger named AUDIT,
 * which logback routes to logs/audit.log. Never pass passwords or tokens.
 */
public final class Audit {
    private static final Logger LOG = LoggerFactory.getLogger("AUDIT");

    private Audit() {}

    /** @param event short UPPER_SNAKE_CASE name, e.g. LOAN_APPROVED */
    public static void event(String event, String detailsPattern, Object... args) {
        Object[] safe = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            safe[i] = args[i] instanceof CharSequence text ? oneLine(text) : args[i];
        }
        LOG.info(event + " " + detailsPattern, safe);
    }

    /** User input such as usernames must not be able to start a fake log line. */
    static String oneLine(CharSequence text) {
        return text.toString().replaceAll("[\\r\\n\\t]+", "_");
    }
}
