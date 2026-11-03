package com.helmifr.library.book;

/** Dropdown entry for the loan form. {@code onLoan}: no copy is on the shelf. */
public record BookOption(Long id, String title, String authorName, long availableCopies, long totalCopies,
                         boolean onLoan) {}
