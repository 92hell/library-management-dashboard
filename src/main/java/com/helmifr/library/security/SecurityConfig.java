package com.helmifr.library.security;

import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.demo.DemoProperties;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Session-cookie authentication for the bundled React app.
 * <ul>
 *   <li>Customers browse the catalogue and use their own area (/api/my/**); everything else under
 *       /api/** is for librarians. Profile and notifications are available to both.</li>
 *   <li>CSRF protection uses the cookie-to-header pattern (XSRF-TOKEN cookie / X-XSRF-TOKEN header).</li>
 *   <li>Unauthenticated API calls get 401 (no redirects); forbidden ones get 403.</li>
 *   <li>Every request re-checks the account (AccountSessionFilter): disabled accounts and changed
 *       passwords end existing sessions.</li>
 *   <li>Demo logins (demo mode) cannot administer accounts or change credentials.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            // Book covers: Open Library redirects cover requests to archive.org hosts.
            "img-src 'self' data: https://covers.openlibrary.org https://archive.org https://*.archive.org",
            "font-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
                                            SecurityContextRepository securityContextRepository,
                                            UserAccountRepository accounts, DemoProperties demo) throws Exception {
        // Public demo logins may run the desk, but never touch accounts or credentials.
        AuthorizationManager<RequestAuthorizationContext> notDemoAccount = (auth, ctx) -> new AuthorizationDecision(
                !(auth.get().getPrincipal() instanceof AccountPrincipal p && demo.isDemoAccount(p.getUsername())));
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(new AccountSessionFilter(accounts), AuthorizationFilter.class)
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                        // Receipt verification (QR link) and the signing public key are public.
                        .requestMatchers(HttpMethod.GET, "/api/receipts/verify/**", "/api/receipts/public-key").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/receipts/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/profile", "/api/profile/card").authenticated()
                        .requestMatchers("/api/profile/**", "/api/profile")
                        .access(AuthorizationManagers.allOf(AuthenticatedAuthorizationManager.authenticated(), notDemoAccount))
                        .requestMatchers("/api/users/**", "/api/users")
                        .access(AuthorizationManagers.allOf(AuthorityAuthorizationManager.hasRole(Role.LIBRARIAN.name()), notDemoAccount))
                        .requestMatchers("/api/notifications/**", "/api/notifications").authenticated()
                        .requestMatchers("/api/my/**", "/api/my").hasRole(Role.CUSTOMER.name())
                        // Copies (with borrower names) are staff-only, even though the catalogue is not.
                        .requestMatchers("/api/books/*/copies", "/api/copies/**", "/api/books/lookup")
                        .hasRole(Role.LIBRARIAN.name())
                        .requestMatchers(HttpMethod.GET, "/api/books/**", "/api/books", "/api/authors/**", "/api/authors")
                        .authenticated()
                        .requestMatchers("/api/**").hasRole(Role.LIBRARIAN.name())
                        // Everything else is the static React app and its client-side routes.
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Applied on login: new session id (prevents session fixation) and a fresh CSRF token. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
