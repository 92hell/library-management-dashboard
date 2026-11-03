package com.helmifr.library.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code library.security.*}. The librarian account is created on first start if it
 * does not exist yet (see LibrarianBootstrap); an empty password means "generate one and log it".
 */
@ConfigurationProperties(prefix = "library.security")
public record SecurityProperties(Account librarian) {
    public record Account(String username, String password) {}
}
