package com.helmifr.library.loan;

import java.util.Locale;

/** Status filter for the librarian's loan list. OVERDUE is derived: ACTIVE and past the due date. */
public enum LoanFilter {
    ALL, PENDING, WAITLISTED, READY, ACTIVE, OVERDUE, RETURNED, REJECTED, CANCELLED, EXPIRED;

    static LoanFilter parse(String value) {
        try {
            return value == null ? ALL : valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }

    LoanStatus status() {
        return switch (this) {
            case ALL -> null;
            case OVERDUE -> LoanStatus.ACTIVE;
            default -> LoanStatus.valueOf(name());
        };
    }
}
