package com.helmifr.library.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The real browser flow: cookie-to-header CSRF, session login, token rotation, logout.
 * <p>
 * Runs in a fresh application context because spring-security-test's {@code csrf()} helper
 * (used by the other test classes) replaces the CSRF token repository of the shared context.
 */
@IntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("Login flow as a browser does it")
class LoginFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired UserAccountRepository accounts;

    @Test
    @DisplayName("bootstrap librarian: /me hands out XSRF cookie, login rotates it, logout ends the session")
    void fullFlow() throws Exception {
        MvcResult anonymous = mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andReturn();
        Cookie xsrf = latestCookie(anonymous, "XSRF-TOKEN");

        MockHttpSession session = new MockHttpSession();
        MvcResult login = mvc.perform(post("/api/auth/login").session(session).cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"librarian\", \"password\": \"librarian-test-pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LIBRARIAN"))
                .andReturn();
        Cookie rotated = latestCookie(login, "XSRF-TOKEN");

        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.username").value("librarian"));
        mvc.perform(post("/api/auth/logout").session(session).cookie(rotated).header("X-XSRF-TOKEN", rotated.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("customers log in with their database account; disabled accounts cannot")
    void customerLoginAndDisabledAccount() throws Exception {
        UserAccount customer = data.customer("Siti");
        Cookie xsrf = latestCookie(mvc.perform(get("/api/auth/me")).andReturn(), "XSRF-TOKEN");
        String body = "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(customer.getUsername(), TestData.PASSWORD);

        mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.memberId").value(customer.getMember().getId()));

        customer.setEnabled(false);
        accounts.saveAndFlush(customer);
        mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    /** Logs in through the real flow; returns the session and its current XSRF cookie. */
    private Object[] signIn(String username, String password) throws Exception {
        Cookie xsrf = latestCookie(mvc.perform(get("/api/auth/me")).andReturn(), "XSRF-TOKEN");
        MockHttpSession session = new MockHttpSession();
        MvcResult login = mvc.perform(post("/api/auth/login").session(session).cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return new Object[] {session, latestCookie(login, "XSRF-TOKEN")};
    }

    @Test
    @DisplayName("disabling an account ends its existing sessions")
    void disablingEndsSessions() throws Exception {
        UserAccount customer = data.customer("Siti");
        MockHttpSession session = (MockHttpSession) signIn(customer.getUsername(), TestData.PASSWORD)[0];
        mvc.perform(get("/api/my/loans").session(session)).andExpect(status().isOk());

        customer.setEnabled(false);
        accounts.saveAndFlush(customer);

        mvc.perform(get("/api/my/loans").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("changing the password ends the account's other sessions, but not the current one")
    void passwordChangeEndsOtherSessions() throws Exception {
        UserAccount customer = data.customer("Siti");
        Object[] laptop = signIn(customer.getUsername(), TestData.PASSWORD);
        Object[] phone = signIn(customer.getUsername(), TestData.PASSWORD);
        MockHttpSession laptopSession = (MockHttpSession) laptop[0];
        Cookie laptopXsrf = (Cookie) laptop[1];

        mvc.perform(put("/api/profile/password").session(laptopSession).cookie(laptopXsrf)
                        .header("X-XSRF-TOKEN", laptopXsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"%s\", \"newPassword\": \"a-brand-new-password\"}"
                                .formatted(TestData.PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(laptopSession)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session((MockHttpSession) phone[0])).andExpect(status().isUnauthorized());
    }

    /** A response can clear and re-set the same cookie; the browser keeps the last non-empty one. */
    private static Cookie latestCookie(MvcResult result, String name) {
        Cookie found = null;
        for (Cookie c : result.getResponse().getCookies()) {
            if (name.equals(c.getName()) && !c.getValue().isEmpty()) {
                found = c;
            }
        }
        return found;
    }
}
