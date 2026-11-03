package com.helmifr.library.loan;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * A loan recorded or edited by a librarian. Give the scanned {@code copyBarcode} to lend that exact
 * copy, or just the {@code bookId} to lend any copy on the shelf. borrowDate defaults to today and
 * dueDate to borrowDate + the loan period; a returnDate makes it a RETURNED loan.
 */
public record LoanRequest(
        Long bookId,
        @Size(max = 40) String copyBarcode,
        @NotNull Long memberId,
        @PastOrPresent LocalDate borrowDate,
        LocalDate dueDate,
        @PastOrPresent LocalDate returnDate) {

    @JsonIgnore
    @AssertTrue(message = "{library.validation.bookOrCopy}")
    public boolean isBookIdOrCopyBarcodeGiven() {
        return bookId != null || (copyBarcode != null && !copyBarcode.isBlank());
    }

    @JsonIgnore
    @AssertTrue(message = "{library.validation.notBeforeBorrowDate}")
    public boolean isReturnDateAfterBorrowDate() {
        return returnDate == null || borrowDate == null || !returnDate.isBefore(borrowDate);
    }

    @JsonIgnore
    @AssertTrue(message = "{library.validation.notBeforeBorrowDate}")
    public boolean isDueDateAfterBorrowDate() {
        return dueDate == null || borrowDate == null || !dueDate.isBefore(borrowDate);
    }
}
