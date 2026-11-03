package com.helmifr.library.loan;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Small request bodies for the loan workflow endpoints. */
public final class LoanDecisions {
    private LoanDecisions() {}

    /** Customer asks to borrow a book. */
    public record BorrowRequest(@NotNull Long bookId) {}

    /** Librarian approves a request; dueDate defaults to today + the loan period. */
    public record Approval(@FutureOrPresent LocalDate dueDate) {}

    /** Librarian rejects a request; the reason is shown to the customer. */
    public record Rejection(@Size(max = 500) String reason) {}
}
