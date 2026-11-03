package com.helmifr.library.loan;

import com.helmifr.library.common.Ref;
import java.time.Instant;
import java.time.LocalDate;

public record LoanResponse(Long id, LoanBook book, LoanCopy copy, Ref member, LoanStatus status, Instant requestedAt,
                           LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate, boolean overdue,
                           String decisionNote) {

    public record LoanBook(Long id, String title, String authorName) {}

    /** The physical copy; null for requests that have not been handed over. */
    public record LoanCopy(Long id, String barcode) {}

    static LoanResponse from(Loan l, LocalDate today) {
        return new LoanResponse(l.getId(),
                new LoanBook(l.getBook().getId(), l.getBook().getTitle(), l.getBook().getAuthor().getName()),
                l.getCopy() == null ? null : new LoanCopy(l.getCopy().getId(), l.getCopy().getBarcode()),
                new Ref(l.getMember().getId(), l.getMember().getName()),
                l.getStatus(), l.getRequestedAt(), l.getBorrowDate(), l.getDueDate(), l.getReturnDate(),
                l.isOverdue(today), l.getDecisionNote());
    }
}
