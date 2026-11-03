package com.helmifr.library.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Login attempt limiter")
class LoginAttemptLimiterTest {

    @Test
    @DisplayName("5 failures for one username from one address block that pair only")
    void perUsernameAndAddress() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("siti", "203.0.113.5");
        }
        assertThat(limiter.isBlocked("siti", "203.0.113.5")).isTrue();
        assertThat(limiter.isBlocked("siti", "198.51.100.7")).as("another visitor").isFalse();
    }

    @Test
    @DisplayName("one address trying many usernames (password spraying) is blocked as a whole")
    void perAddress() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES_PER_ADDRESS; i++) {
            limiter.recordFailure("user" + i, "203.0.113.5");
        }
        assertThat(limiter.isBlocked("someone-new", "203.0.113.5")).isTrue();
        assertThat(limiter.isBlocked("someone-new", "198.51.100.7")).isFalse();
    }

    @Test
    @DisplayName("memory stays bounded when an attacker sprays unique usernames")
    void boundedMemory() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC(), 100);
        for (int i = 0; i < 10_000; i++) {
            limiter.recordFailure("user" + i, "10.0.0." + (i % 250));
        }
        assertThat(limiter.trackedEntries()).isLessThanOrEqualTo(100);
    }
}
