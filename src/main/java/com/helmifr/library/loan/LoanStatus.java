package com.helmifr.library.loan;

/**
 * Lifecycle of a loan:
 * <pre>
 *   PENDING --approve--> ACTIVE --return--> RETURNED
 *      |--reject--> REJECTED
 *      '--cancel--> CANCELLED (by the customer)
 * </pre>
 * Walk-in loans recorded at the desk start directly as ACTIVE (or RETURNED for past loans).
 */
public enum LoanStatus {
    PENDING, ACTIVE, RETURNED, REJECTED, CANCELLED;

    public boolean isOpen() {
        return this == PENDING || this == ACTIVE;
    }
}
