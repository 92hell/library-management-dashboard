package com.helmifr.library.security;

import com.helmifr.library.account.AccountDtos.RegisterRequest;
import com.helmifr.library.account.AccountDtos.SessionUser;
import com.helmifr.library.account.AccountService;
import com.helmifr.library.common.Audit;
import com.helmifr.library.common.FieldValidationException;
import com.helmifr.library.demo.DemoProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

/** Session login/logout, self-registration and demo account info for the React app. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    public record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}

    public record DemoAccount(String label, String username, String password) {}

    public record DemoInfo(boolean enabled, List<DemoAccount> accounts) {}

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;
    private final LoginAttemptLimiter limiter;
    private final AccountService accountService;
    private final DemoProperties demo;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    @GetMapping("/me")
    public ResponseEntity<SessionUser> me() {
        return CurrentUser.principal()
                .map(p -> ResponseEntity.ok(accountService.sessionUser(p.getId())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest login,
                                   HttpServletRequest request, HttpServletResponse response) {
        String address = request.getRemoteAddr();
        if (limiter.isBlocked(login.username(), address)) {
            Audit.event("LOGIN_BLOCKED", "username={} ip={}", login.username(), address);
            return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in a few minutes.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        } catch (AuthenticationException e) {
            limiter.recordFailure(login.username(), address);
            Audit.event("LOGIN_FAILED", "username={} ip={} reason={}", login.username(), address,
                    e.getClass().getSimpleName());
            return problem(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        limiter.recordSuccess(login.username(), address);

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        writeCsrfCookie(request);

        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        Audit.event("LOGIN_SUCCEEDED", "username={} role={} ip={}", principal.getUsername(), principal.getRole(), address);
        return ResponseEntity.ok(accountService.sessionUser(principal.getId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        if (authentication != null) {
            Audit.event("LOGOUT", "username={}", authentication.getName());
        }
        new CsrfLogoutHandler(csrfTokenRepository).logout(request, response, authentication);
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    /** Customer self-registration. Failed member-code matches count towards the address's lockout. */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registration, HttpServletRequest request) {
        String address = request.getRemoteAddr();
        if (limiter.isBlocked("register", address)) {
            return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in a few minutes.");
        }
        try {
            SessionUser created = accountService.register(registration);
            return ResponseEntity.created(URI.create("/api/profile")).body(created);
        } catch (FieldValidationException e) {
            if (e.getFieldErrors().containsKey("memberCode")) {
                limiter.recordFailure("register", address);
                Audit.event("REGISTER_LINK_FAILED", "username={} ip={}", registration.username(), address);
            }
            throw e;
        }
    }

    /** Lets the login page offer the demo accounts when the app runs in demo mode. */
    @GetMapping("/demo")
    public DemoInfo demo() {
        if (!demo.enabled()) {
            return new DemoInfo(false, List.of());
        }
        return new DemoInfo(true, List.of(
                new DemoAccount("Customer", demo.customerUsername(), demo.customerPassword()),
                new DemoAccount("Librarian", demo.librarianUsername(), demo.librarianPassword())));
    }

    /** The CSRF token was rotated on login; resolve it now so the new cookie goes out with this response. */
    private static void writeCsrfCookie(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status).body(pd);
    }
}
