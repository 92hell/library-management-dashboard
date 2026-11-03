package com.helmifr.library.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caps successful self-registrations per network address (default 3 an hour), so the sign-up form
 * cannot be used to mass-create accounts. In memory and bounded, like LoginAttemptLimiter.
 */
@Component
public class SignUpLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_TRACKED = 10_000;

    private final Clock clock;
    private final int maxPerHour;
    private final Map<String, Deque<Instant>> signUps = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    public SignUpLimiter(Clock clock, @Value("${library.security.max-signups-per-hour:3}") int maxPerHour) {
        this.clock = clock;
        this.maxPerHour = maxPerHour;
    }

    public synchronized boolean isBlocked(String address) {
        Deque<Instant> recent = recent(address);
        return recent != null && recent.size() >= maxPerHour;
    }

    public synchronized void recordSignUp(String address) {
        signUps.computeIfAbsent(address, a -> new ArrayDeque<>()).addLast(clock.instant());
    }

    private Deque<Instant> recent(String address) {
        Deque<Instant> times = signUps.get(address);
        if (times == null) {
            return null;
        }
        Instant cutoff = clock.instant().minus(WINDOW);
        for (Iterator<Instant> it = times.iterator(); it.hasNext(); ) {
            if (it.next().isBefore(cutoff)) {
                it.remove();
            }
        }
        return times;
    }
}
