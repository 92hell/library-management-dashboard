package com.helmifr.library.account;

import com.helmifr.library.common.Audit;
import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.demo.DemoProperties;
import com.helmifr.library.security.PasswordResetLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password". A request creates a single-use link valid for 30 minutes and emails it (after
 * commit, see PasswordResetMailConfig); asking again replaces the earlier link. Using the link sets
 * the new password, which also ends every session of the account (their credential stamp no longer
 * matches). Whether an account matched is never told to the person asking.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResets {

    static final Duration VALID_FOR = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository accounts;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final PasswordResetLimiter limiter;
    private final DemoProperties demo;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Off in the public demo (its accounts are shared) and when no email can be sent. */
    @Transactional(readOnly = true)
    public boolean available() {
        return mailer.isAvailable() && !demo.enabled();
    }

    /** Sends a link to each enabled account whose username or email is {@code identifier}. */
    public void request(String identifier) {
        Instant now = clock.instant();
        tokens.deleteExpired(now);
        for (UserAccount account : accounts.findByUsernameOrEmail(identifier.trim())) {
            if (!account.isEnabled() || demo.isDemoAccount(account.getUsername())) {
                continue;
            }
            if (!limiter.allowLink(account.getId())) {
                Audit.event("PASSWORD_RESET_LIMITED", "username={}", account.getUsername());
                continue;
            }
            tokens.deleteAllForAccount(account.getId());
            String token = newToken();
            PasswordResetToken stored = new PasswordResetToken();
            stored.setAccountId(account.getId());
            stored.setTokenHash(hash(token));
            stored.setCreatedAt(now);
            stored.setExpiresAt(now.plus(VALID_FOR));
            tokens.save(stored);
            events.publishEvent(new PasswordResetRequested(account.getId(), account.getUsername(), token));
            Audit.event("PASSWORD_RESET_REQUESTED", "username={}", account.getUsername());
        }
    }

    /** Sets the new password if the link is still valid; the link (and any other) then stops working. */
    public void reset(String token, String newPassword) {
        PasswordResetToken stored = tokens.findByTokenHash(hash(token))
                .filter(t -> t.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(PasswordResets::invalidLink);
        UserAccount account = accounts.findById(stored.getAccountId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(PasswordResets::invalidLink);
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        tokens.deleteAllForAccount(account.getId());
        Audit.event("PASSWORD_RESET_DONE", "username={}", account.getUsername());
    }

    /** The same answer for an unknown, used, replaced or expired link, and for a disabled account. */
    private static FieldValidationException invalidLink() {
        return new FieldValidationException("token", "This link is invalid or has expired. Ask for a new one.");
    }

    /** 32 random bytes, URL-safe (43 characters). */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
