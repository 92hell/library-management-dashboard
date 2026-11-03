package com.helmifr.library.book;

/** Dropdown entry for the loan form. */
public record BookOption(Long id, String title, String authorName, boolean onLoan) {}
