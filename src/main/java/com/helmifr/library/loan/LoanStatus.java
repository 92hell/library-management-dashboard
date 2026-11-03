package com.helmifr.library.loan;

/**
 * Lifecycle of a loan:
 * <pre>
 *   PENDING --approve--> READY (copy set aside until readyUntil) --hand over--> ACTIVE --return--> RETURNED
 *      |           '---> WAITLISTED (no copy free) --copy comes back--> READY
 *      |--reject--> REJECTED   (also from WAITLISTED / READY)
 *      '--cancel--> CANCELLED  (by the customer, from PENDING / WAITLISTED / READY)
 *   READY --not collected in time--> EXPIRED
 * </pre>
 * Walk-in loans recorded at the desk start directly as ACTIVE (or RETURNED for past loans).
 */
public enum LoanStatus {
    PENDING, WAITLISTED, READY, ACTIVE, RETURNED, REJECTED, CANCELLED, EXPIRED;

    /** Counts towards the member's open requests/loans. */
    public boolean isOpen() {
        return this == PENDING || this == WAITLISTED || this == READY || this == ACTIVE;
    }

    /** Holds a copy: lent out, or set aside on the holds shelf. */
    public boolean holdsCopy() {
        return this == READY || this == ACTIVE;
    }
}
