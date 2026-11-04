package com.helmifr.library.security;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caps successful self-registrations per network address (default 3 an hour), so the sign-up form
 * cannot be used to mass-create accounts. In memory and bounded, like LoginAttemptLimiter.
 */
@Component
public class SignUpLimiter {

    private final HourlyCounter signUps;

    public SignUpLimiter(Clock clock, @Value("${library.security.max-signups-per-hour:3}") int maxPerHour) {
        this.signUps = new HourlyCounter(clock, maxPerHour);
    }

    public boolean isBlocked(String address) {
        return signUps.isBlocked(address);
    }

    public void recordSignUp(String address) {
        signUps.record(address);
    }
}
