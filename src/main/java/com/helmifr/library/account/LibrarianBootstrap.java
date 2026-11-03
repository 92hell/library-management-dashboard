package com.helmifr.library.account;

import com.helmifr.library.security.SecurityProperties;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Makes sure the configured first librarian exists, so a fresh installation can be signed in to.
 * Does nothing if that username already exists.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class LibrarianBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LibrarianBootstrap.class);

    private final SecurityProperties properties;
    private final UserAccountRepository accounts;
    private final AccountService accountService;

    @Override
    public void run(ApplicationArguments args) {
        String username = properties.librarian().username();
        if (accounts.existsByUsernameIgnoreCase(username)) {
            return;
        }
        String password = properties.librarian().password();
        if (password == null || password.isBlank()) {
            password = randomPassword();
            log.warn("LIBRARIAN_PASSWORD is not set. Created librarian '{}' with generated password: {} "
                    + "(change it after signing in)", username, password);
        }
        accountService.newLibrarian(username, password, "Head Librarian", null);
        log.info("Created initial librarian account '{}'", username);
    }

    private static String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
