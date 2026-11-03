package com.helmifr.library.common;

import com.helmifr.library.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after Spring Security and adds the signed-in username to the logging context. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class UserLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CurrentUser.principal().ifPresent(p -> {
            MDC.put(RequestLoggingFilter.MDC_USER, p.getUsername());
            request.setAttribute(RequestLoggingFilter.USER_ATTRIBUTE, p.getUsername());
        });
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestLoggingFilter.MDC_USER);
        }
    }
}
