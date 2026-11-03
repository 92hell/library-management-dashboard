package com.helmifr.library.account;

import com.helmifr.library.common.Audit;
import com.helmifr.library.security.SecurityProperties;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes sure the configured owner librarian (LIBRARIAN_USERNAME) can always sign in:
 * <ul>
 *   <li>creates it on first start;</li>
 *   <li>re-enables it if it was disabled;</li>
 *   <li>when LIBRARIAN_PASSWORD is set, resets the password to it - the recovery path if the
 *       account was taken over or the password forgotten.</li>
 * </ul>
 * A generated password (no LIBRARIAN_PASSWORD) is printed to the console once and never written
 * to the log files.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class LibrarianBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LibrarianBootstrap.class);

    private final SecurityProperties properties;
    private final UserAccountRepository accounts;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = properties.librarian().username();
        String configured = properties.librarian().password();
        boolean hasConfiguredPassword = configured != null && !configured.isBlank();

        accounts.findByUsernameIgnoreCase(username).ifPresentOrElse(owner -> {
            if (!owner.isEnabled() || owner.getRole() != Role.LIBRARIAN) {
                owner.setEnabled(true);
                owner.setRole(Role.LIBRARIAN);
                Audit.event("OWNER_ACCOUNT_RESTORED", "username={}", username);
            }
            if (hasConfiguredPassword && !passwordEncoder.matches(configured, owner.getPasswordHash())) {
                owner.setPasswordHash(passwordEncoder.encode(configured));
                Audit.event("OWNER_PASSWORD_RESET_FROM_CONFIG", "username={}", username);
            }
        }, () -> {
            String password = hasConfiguredPassword ? configured : randomPassword();
            accountService.newLibrarian(username, password, "Head Librarian", null);
            log.info("Created initial librarian account '{}'", username);
            if (!hasConfiguredPassword) {
                log.warn("LIBRARIAN_PASSWORD is not set; a generated password was printed to the console. "
                        + "Set LIBRARIAN_PASSWORD for any shared or hosted installation.");
                // Deliberately System.out, not the logger: the password must not reach the log files.
                System.out.printf("%n=== Initial librarian '%s' password: %s ===%n%n", username, password);
            }
        });
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
