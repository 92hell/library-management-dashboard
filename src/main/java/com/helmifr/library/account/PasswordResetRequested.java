package com.helmifr.library.account;

/**
 * A reset link to email, published once the request is stored. {@code token} is the secret part of
 * the link; it exists only in memory and in the email, never in the database or the logs.
 */
public record PasswordResetRequested(Long accountId, String username, String token) {

    @Override
    public String toString() {
        return "PasswordResetRequested[accountId=" + accountId + ", username=" + username + "]";
    }
}
