package com.helmifr.library.loan;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/**
 * A loan recorded or edited by a librarian. borrowDate defaults to today and dueDate to
 * borrowDate + the loan period; a returnDate makes it a RETURNED loan.
 */
public record LoanRequest(
        @NotNull Long bookId,
        @NotNull Long memberId,
        @PastOrPresent LocalDate borrowDate,
        LocalDate dueDate,
        @PastOrPresent LocalDate returnDate) {

    @JsonIgnore
    @AssertTrue(message = "must not be before the borrow date")
    public boolean isReturnDateAfterBorrowDate() {
        return returnDate == null || borrowDate == null || !returnDate.isBefore(borrowDate);
    }

    @JsonIgnore
    @AssertTrue(message = "must not be before the borrow date")
    public boolean isDueDateAfterBorrowDate() {
        return dueDate == null || borrowDate == null || !dueDate.isBefore(borrowDate);
    }
}
