package com.helmifr.library.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts events per key (a network address, an account) over the last hour and says when a key
 * has reached its limit. In memory and bounded: the least recently used keys are forgotten first.
 */
public class HourlyCounter {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_TRACKED = 10_000;

    private final Clock clock;
    private final int maxPerHour;
    private final Map<String, Deque<Instant>> events = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
            return size() > MAX_TRACKED;
        }
    };

    public HourlyCounter(Clock clock, int maxPerHour) {
        this.clock = clock;
        this.maxPerHour = maxPerHour;
    }

    public synchronized boolean isBlocked(String key) {
        Deque<Instant> recent = recent(key);
        return recent != null && recent.size() >= maxPerHour;
    }

    public synchronized void record(String key) {
        events.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(clock.instant());
    }

    private Deque<Instant> recent(String key) {
        Deque<Instant> times = events.get(key);
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
