package com.helmifr.library.fine;

/** UNPAID until settled; ACCRUING is never stored: it describes a loan that is still out and overdue. */
public enum FineStatus {
    ACCRUING, UNPAID, PAID, WAIVED
}
