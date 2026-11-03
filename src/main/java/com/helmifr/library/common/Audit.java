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
        LOG.info(event + " " + detailsPattern, args);
    }
}
