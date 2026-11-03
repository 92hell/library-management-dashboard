package com.helmifr.library.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing: after {@value #MAX_FAILURES} failed logins for the same
 * username from the same address within the window, further attempts are refused for a while.
 * In-memory, so it is per instance and resets on restart - enough for a single-node app.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(5);
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String username, String remoteAddress) {
        Attempts a = attempts.get(key(username, remoteAddress));
        return a != null && a.lockedUntil != null && clock.instant().isBefore(a.lockedUntil);
    }

    public void recordFailure(String username, String remoteAddress) {
        Instant now = clock.instant();
        if (attempts.size() > MAX_TRACKED) {
            attempts.values().removeIf(a -> a.windowStart.plus(WINDOW).isBefore(now));
        }
        attempts.compute(key(username, remoteAddress), (k, a) -> {
            if (a == null || a.windowStart.plus(WINDOW).isBefore(now)) {
                a = new Attempts(now);
            }
            a.failures++;
            if (a.failures >= MAX_FAILURES) {
                a.lockedUntil = now.plus(LOCKOUT);
                a.failures = 0;
                a.windowStart = now;
            }
            return a;
        });
    }

    public void recordSuccess(String username, String remoteAddress) {
        attempts.remove(key(username, remoteAddress));
    }

    private static String key(String username, String remoteAddress) {
        return username.toLowerCase(Locale.ROOT) + "|" + remoteAddress;
    }

    private static final class Attempts {
        Instant windowStart;
        int failures;
        Instant lockedUntil;

        Attempts(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
