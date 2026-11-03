package com.helmifr.library.loan;

import com.helmifr.library.common.Ref;
import java.time.Instant;
import java.time.LocalDate;

/**
 * @param readyUntil    READY holds: collect by this date
 * @param queuePosition WAITLISTED requests: 1 = next in line
 * @param renewals      how many times the loan was renewed
 * @param canRenew      an ACTIVE loan that may be renewed now
 */
public record LoanResponse(Long id, LoanBook book, LoanCopy copy, Ref member, LoanStatus status, Instant requestedAt,
                           LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate, boolean overdue,
                           String decisionNote, LocalDate readyUntil, Integer queuePosition, int renewals,
                           boolean canRenew) {

    public record LoanBook(Long id, String title, String authorName) {}

    /** The physical copy; null for requests that have not been given a copy. */
    public record LoanCopy(Long id, String barcode) {}

    static LoanResponse from(Loan l, LocalDate today, Integer queuePosition, boolean canRenew) {
        return new LoanResponse(l.getId(),
                new LoanBook(l.getBook().getId(), l.getBook().getTitle(), l.getBook().getAuthor().getName()),
                l.getCopy() == null ? null : new LoanCopy(l.getCopy().getId(), l.getCopy().getBarcode()),
                new Ref(l.getMember().getId(), l.getMember().getName()),
                l.getStatus(), l.getRequestedAt(), l.getBorrowDate(), l.getDueDate(), l.getReturnDate(),
                l.isOverdue(today), l.getDecisionNote(), l.getReadyUntil(), queuePosition, l.getRenewalCount(),
                canRenew);
    }
}
