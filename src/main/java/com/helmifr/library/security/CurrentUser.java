package com.helmifr.library.security;

import com.helmifr.library.account.Role;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the signed-in user from controllers and services. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<AccountPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    /** Id of the signed-in account; only call on endpoints that require authentication. */
    public static Long id() {
        return principal().map(AccountPrincipal::getId)
                .orElseThrow(() -> new IllegalStateException("No signed-in user"));
    }

    /** Member id of the signed-in customer; only call on customer-only endpoints. */
    public static Long memberId() {
        return principal().map(AccountPrincipal::getMemberId)
                .orElseThrow(() -> new IllegalStateException("Signed-in user has no member record"));
    }

    public static String username() {
        return principal().map(AccountPrincipal::getUsername).orElse("anonymous");
    }

    public static boolean is(Role role) {
        return principal().map(p -> p.getRole() == role).orElse(false);
    }
}
