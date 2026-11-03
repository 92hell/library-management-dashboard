package com.helmifr.library.security;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.common.Audit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-checks the signed-in account on every request. A session ends (401) when its account was
 * disabled or deleted, or its password changed since the session started - so revoking access
 * takes effect immediately instead of when the session expires.
 */
final class AccountSessionFilter extends OncePerRequestFilter {

    private final UserAccountRepository accounts;

    AccountSessionFilter(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<AccountPrincipal> principal = CurrentUser.principal();
        if (principal.isPresent() && !stillValid(principal.get())) {
            AccountPrincipal p = principal.get();
            Audit.event("SESSION_REVOKED", "username={}", p.getUsername());
            new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean stillValid(AccountPrincipal p) {
        Optional<UserAccount> account = accounts.findById(p.getId());
        return account.isPresent()
                && account.get().isEnabled()
                && account.get().getRole() == p.getRole()
                && AccountPrincipal.stampOf(account.get()).equals(p.getCredentialStamp());
    }
}
