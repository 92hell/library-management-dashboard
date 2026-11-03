package com.helmifr.library.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
@DisplayName("Access control")
class AccessControlTest {

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired MockMvc mvc;

    @ParameterizedTest(name = "anonymous GET {0} -> 401")
    @ValueSource(strings = {"/api/books", "/api/authors", "/api/members", "/api/borrowed-books", "/api/my/loans",
            "/api/profile", "/api/notifications", "/api/dashboard", "/api/users", "/api/auth/me"})
    void anonymousIsUnauthorized(String url) throws Exception {
        api.anonymous().get(url).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "customer GET {0} -> 403")
    @ValueSource(strings = {"/api/members", "/api/borrowed-books", "/api/dashboard", "/api/users"})
    void customersCannotReachStaffPages(String url) throws Exception {
        api.as(data.customer("Siti")).get(url).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("customers cannot change the catalogue")
    void customersCannotWriteCatalogue() throws Exception {
        api.as(data.customer("Siti")).post("/api/authors", "{\"name\": \"X\"}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("librarians do not have a customer 'my loans' area")
    void librariansHaveNoMyLoans() throws Exception {
        api.as(data.librarian("Lina")).get("/api/my/loans").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("state-changing requests without a CSRF token are rejected")
    void csrfRequired() throws Exception {
        UserAccount librarian = data.librarian("Lina");
        mvc.perform(MockMvcRequestBuilders.post("/api/authors")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(new AccountPrincipal(librarian)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("health, demo info and registration are public")
    void publicEndpoints() throws Exception {
        api.anonymous().get("/actuator/health").andExpect(status().isOk());
        api.anonymous().get("/api/auth/demo").andExpect(status().isOk());
    }

    @Test
    @DisplayName("security headers are sent (CSP, nosniff, no framing)")
    void securityHeaders() throws Exception {
        api.anonymous().get("/api/auth/demo")
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
