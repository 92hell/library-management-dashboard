package com.helmifr.library.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing with two counters, both per visitor address (the real client
 * address, see server.forward-headers-strategy):
 * <ul>
 *   <li>{@value #MAX_FAILURES} failures for one username from one address lock that pair;</li>
 *   <li>{@value #MAX_FAILURES_PER_ADDRESS} failures from one address, across any usernames
 *       (password spraying), lock the address.</li>
 * </ul>
 * Locks last {@code LOCKOUT}. Other visitors are never affected. In memory and bounded (least
 * recently used entries are dropped), so it is per instance and resets on restart.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final int MAX_FAILURES_PER_ADDRESS = 20;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_TRACKED = 10_000;

    private final Clock clock;
    private final Map<String, Attempts> attempts;

    @Autowired
    public LoginAttemptLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_TRACKED);
    }

    LoginAttemptLimiter(Clock clock, int maxTracked) {
        this.clock = clock;
        this.attempts = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Attempts> eldest) {
                return size() > maxTracked;
            }
        };
    }

    public synchronized boolean isBlocked(String username, String remoteAddress) {
        Instant now = clock.instant();
        return locked(pairKey(username, remoteAddress), now) || locked(addressKey(remoteAddress), now);
    }

    public synchronized void recordFailure(String username, String remoteAddress) {
        Instant now = clock.instant();
        count(pairKey(username, remoteAddress), MAX_FAILURES, now);
        count(addressKey(remoteAddress), MAX_FAILURES_PER_ADDRESS, now);
    }

    public synchronized void recordSuccess(String username, String remoteAddress) {
        attempts.remove(pairKey(username, remoteAddress));
    }

    synchronized int trackedEntries() {
        return attempts.size();
    }

    private boolean locked(String key, Instant now) {
        Attempts a = attempts.get(key);
        return a != null && a.lockedUntil != null && now.isBefore(a.lockedUntil);
    }

    private void count(String key, int limit, Instant now) {
        Attempts a = attempts.get(key);
        if (a == null || a.windowStart.plus(WINDOW).isBefore(now)) {
            a = new Attempts(now);
            attempts.put(key, a);
        }
        a.failures++;
        if (a.failures >= limit) {
            a.lockedUntil = now.plus(LOCKOUT);
            a.failures = 0;
            a.windowStart = now;
        }
    }

    private static String pairKey(String username, String remoteAddress) {
        return "user:" + username.toLowerCase(Locale.ROOT) + "|" + remoteAddress;
    }

    private static String addressKey(String remoteAddress) {
        return "addr:" + remoteAddress;
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
