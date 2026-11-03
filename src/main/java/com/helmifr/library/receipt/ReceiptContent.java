package com.helmifr.library.receipt;

import java.util.List;

/**
 * What a receipt says, as signed. Serialised to canonical JSON (properties A-Z, dates as ISO
 * strings) so the same receipt always gives the same bytes and the same signature.
 */
public record ReceiptContent(String number, String library, String issuedAt, String issuedBy, LoanPart loan,
                             List<EventPart> events, FinePart fine) {

    public record LoanPart(Long id, String status, String bookTitle, String authorName, String copyBarcode,
                           String memberName, String libraryCardId, String requestedAt, String borrowDate,
                           String dueDate, String returnDate, int renewals) {}

    public record EventPart(String type, String status, String occurredAt, String actor, String detail) {}

    public record FinePart(long amount, String currency, String status, int daysLate) {}
}
