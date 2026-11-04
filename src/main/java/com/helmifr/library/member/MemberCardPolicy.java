package com.helmifr.library.member;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Library card rules, bound from {@code library.members.*}.
 *
 * @param cardValidityMonths how long a new or renewed card is valid (0 = cards do not expire)
 * @param cardReminderDays   how many days before the expiry date the member is reminded (0 = never)
 */
@ConfigurationProperties(prefix = "library.members")
public record MemberCardPolicy(int cardValidityMonths, int cardReminderDays) {

    /** Last valid day of a card issued on {@code issued}, or null if cards do not expire. */
    public LocalDate expiryFrom(LocalDate issued) {
        return cardValidityMonths <= 0 ? null : issued.plusMonths(cardValidityMonths);
    }

    /**
     * The new expiry when a card is renewed on {@code today}: a full period from today if it has
     * expired (or never had a date), else from its current expiry, so renewing early loses no days.
     */
    public LocalDate renewed(LocalDate currentExpiry, LocalDate today) {
        LocalDate from = currentExpiry == null || currentExpiry.isBefore(today) ? today : currentExpiry;
        return expiryFrom(from);
    }
}
