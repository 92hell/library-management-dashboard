package com.helmifr.library.loan;

/** What happened to a loan; one row in its history (and on its receipt). */
public enum LoanEventType {
    /** A loan from before the history was kept (one entry describing its state then). */
    IMPORTED,
    /** The member asked to borrow the book online. */
    REQUESTED,
    /** A librarian lent the book at the desk (or recorded a past loan). */
    LENT,
    /** Approved with a copy set aside. */
    APPROVED,
    /** Approved, but every copy is out: in the queue. */
    WAITLISTED,
    /** A returned or new copy was set aside for this member from the queue. */
    READY,
    /** The member collected the copy set aside: the loan started. */
    COLLECTED,
    RENEWED,
    RETURNED,
    REJECTED,
    CANCELLED,
    /** Not collected in time. */
    EXPIRED,
    /** A librarian corrected the loan's details. */
    EDITED,
    DELETED,
    FINE_CHARGED,
    FINE_PAID,
    FINE_WAIVED
}
