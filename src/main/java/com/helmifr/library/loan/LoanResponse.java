package com.helmifr.library.loan;

import com.helmifr.library.common.Ref;
import java.time.Instant;
import java.time.LocalDate;

public record LoanResponse(Long id, LoanBook book, Ref member, LoanStatus status, Instant requestedAt,
                           LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate, boolean overdue,
                           String decisionNote) {

    public record LoanBook(Long id, String title, String authorName) {}

    static LoanResponse from(Loan l, LocalDate today) {
        return new LoanResponse(l.getId(),
                new LoanBook(l.getBook().getId(), l.getBook().getTitle(), l.getBook().getAuthor().getName()),
                new Ref(l.getMember().getId(), l.getMember().getName()),
                l.getStatus(), l.getRequestedAt(), l.getBorrowDate(), l.getDueDate(), l.getReturnDate(),
                l.isOverdue(today), l.getDecisionNote());
    }
}
