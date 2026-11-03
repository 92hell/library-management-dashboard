package com.helmifr.library.fine;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Late-fee rules, bound from {@code library.fines.*}. Amounts are whole units of {@code currency}.
 *
 * @param perDay      charged for each day a book is returned late
 * @param maxPerLoan  the most one late return can cost
 * @param blockAt     unpaid fines at or above this stop a member from borrowing (0 = never)
 * @param currency    ISO 4217 code shown with amounts
 */
@ConfigurationProperties(prefix = "library.fines")
public record FinePolicy(long perDay, long maxPerLoan, long blockAt, String currency) {

    /** The fee for a book returned {@code daysLate} days after it was due (0 if not late). */
    public long feeFor(long daysLate) {
        return daysLate <= 0 ? 0 : Math.min(daysLate * perDay, maxPerLoan);
    }

    /** "IDR 25,000". */
    public String format(long amount) {
        return "%s %,d".formatted(currency, amount);
    }

    public boolean blocks(long unpaid) {
        return blockAt > 0 && unpaid >= blockAt;
    }
}
