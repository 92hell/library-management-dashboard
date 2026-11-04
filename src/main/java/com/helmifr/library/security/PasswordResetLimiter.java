package com.helmifr.library.security;

import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Limits "forgot password" requests: 5 an hour from one network address (more are refused), and at
 * most 3 links an hour to one account (more are silently not sent, so the answer still says
 * nothing about whether the account exists, and nobody can flood someone's inbox).
 */
@Component
public class PasswordResetLimiter {

    static final int PER_ADDRESS = 5;
    static final int PER_ACCOUNT = 3;

    private final HourlyCounter addresses;
    private final HourlyCounter accounts;

    public PasswordResetLimiter(Clock clock) {
        this.addresses = new HourlyCounter(clock, PER_ADDRESS);
        this.accounts = new HourlyCounter(clock, PER_ACCOUNT);
    }

    /** Counts this request from {@code address}; false once the address has reached its limit. */
    public boolean allowRequest(String address) {
        if (addresses.isBlocked(address)) {
            return false;
        }
        addresses.record(address);
        return true;
    }

    /** Counts a link to this account; false once the account has had its limit of links this hour. */
    public boolean allowLink(Long accountId) {
        String key = String.valueOf(accountId);
        if (accounts.isBlocked(key)) {
            return false;
        }
        accounts.record(key);
        return true;
    }
}
