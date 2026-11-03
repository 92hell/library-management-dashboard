package com.helmifr.library.fine;

/** A member's unpaid fines dropped below the borrowing limit: holds waiting for them can be served. */
public record FinesSettled(Long memberId) {}
