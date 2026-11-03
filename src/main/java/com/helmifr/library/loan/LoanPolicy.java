package com.helmifr.library.loan;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lending rules, bound from {@code library.loans.*}.
 *
 * @param periodDays        default loan length when no due date is given
 * @param maxOpenPerMember  pending requests + active loans a customer may have at once
 * @param dueSoonDays       how many days before the due date the "due soon" reminder goes out
 * @param reminderCron      when the reminder job runs ("-" disables it)
 */
@ConfigurationProperties(prefix = "library.loans")
public record LoanPolicy(int periodDays, int maxOpenPerMember, int dueSoonDays, String reminderCron) {}
