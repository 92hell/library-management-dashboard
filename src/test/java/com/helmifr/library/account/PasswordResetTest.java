package com.helmifr.library.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * "Forgot password": a single-use link, valid for 30 minutes, sent to the email on the account.
 * The answer never says whether an account matched, so the form cannot be used to find out who
 * has an account. The email itself goes out after the request has finished (see
 * PasswordResetMailTest); here the link is taken from the event that sends it.
 */
@IntegrationTest
@RecordApplicationEvents
@TestPropertySource(properties = "library.notifications.email.enabled=true")
@DisplayName("Password reset by email")
class PasswordResetTest {

    private static final AtomicInteger ADDRESS = new AtomicInteger(10);
    private static final String NEW_PASSWORD = "a-brand-new-password";

    @MockitoBean JavaMailSender mailSender;
    @Autowired MockMvc mvc;
    @Autowired Api api;
    @Autowired TestData data;
    @Autowired ApplicationEvents events;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired UserAccountRepository accounts;
    UserAccount siti;
    String address;

    @BeforeEach
    void setUp() {
        siti = data.customer("Siti");
        address = "203.0.113." + ADDRESS.incrementAndGet();
    }

    private ResultActions ask(String identifier) throws Exception {
        return post("/api/auth/password-reset", "{\"identifier\": \"%s\"}".formatted(identifier));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return post("/api/auth/password-reset/confirm",
                "{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, password));
    }

    private ResultActions post(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(r -> { r.setRemoteAddr(address); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions login(UserAccount account, String password) throws Exception {
        return api.anonymous().post("/api/auth/login",
                "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(account.getUsername(), password));
    }

    private List<PasswordResetRequested> links() {
        return events.stream(PasswordResetRequested.class).toList();
    }

    private String linkFor(UserAccount account) {
        return links().stream().filter(l -> l.accountId().equals(account.getId()))
                .reduce((first, second) -> second).orElseThrow().token();
    }

    @Test
    @DisplayName("asking by username or by email sends a link; the answer is the same when no account matches")
    void sameAnswerEitherWay() throws Exception {
        String known = ask(siti.getUsername()).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        ask(siti.getEmail().toUpperCase()).andExpect(status().isAccepted());
        String unknown = ask("nobody-here").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown).contains("If an account matches");
        assertThat(links()).hasSize(2).allMatch(l -> l.accountId().equals(siti.getId()));
    }

    @Test
    @DisplayName("the link sets a new password once: the old password and the link stop working, and other sessions end")
    void resetOnce() throws Exception {
        UserAccount sessionBefore = accounts.findById(siti.getId()).map(PasswordResetTest::copy).orElseThrow();
        ask(siti.getUsername());
        String token = linkFor(siti);

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login(siti, NEW_PASSWORD).andExpect(status().isOk());
        login(siti, TestData.PASSWORD).andExpect(status().isUnauthorized());
        reset(token, "yet-another-password").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.token").value("This link is invalid or has expired. Ask for a new one."));
        api.as(sessionBefore).get("/api/profile").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a link is valid for 30 minutes, and only the latest link works")
    void expiryAndLatestOnly() throws Exception {
        ask(siti.getUsername());
        String first = linkFor(siti);
        ask(siti.getUsername());
        String second = linkFor(siti);

        reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());

        PasswordResetToken stored = tokens.findAll().stream()
                .filter(t -> t.getAccountId().equals(siti.getId())).findFirst().orElseThrow();
        assertThat(stored.getExpiresAt()).isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        tokens.saveAndFlush(stored);
        reset(second, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("only a hash of the link is stored, so a copy of the database cannot be used to reset a password")
    void onlyHashStored() throws Exception {
        ask(siti.getUsername());
        String token = linkFor(siti);

        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(tokens.findAll()).noneMatch(t -> t.getTokenHash().contains(token));
    }

    @Test
    @DisplayName("the new password must be at least 10 characters; the link still works after a refusal")
    void passwordRules() throws Exception {
        ask(siti.getUsername());
        String token = linkFor(siti);

        reset(token, "short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.newPassword").exists());
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("disabled accounts get no link (the answer is still the same)")
    void disabledAccounts() throws Exception {
        UserAccount disabled = data.customer("Bayu");
        disabled.setEnabled(false);
        accounts.saveAndFlush(disabled);

        ask(disabled.getUsername()).andExpect(status().isAccepted());

        assertThat(links()).isEmpty();
    }

    @Test
    @DisplayName("one network may ask 5 times an hour; one account gets at most 3 links an hour")
    void limits() throws Exception {
        for (int i = 0; i < 5; i++) {
            ask(siti.getUsername()).andExpect(status().isAccepted());
        }
        ask(siti.getUsername()).andExpect(status().isTooManyRequests());
        assertThat(links()).hasSize(3);

        address = "203.0.113." + ADDRESS.incrementAndGet();
        ask(siti.getUsername()).andExpect(status().isAccepted());
        assertThat(links()).hasSize(3);
    }

    @Test
    @DisplayName("the sign-in page is told that password reset is available")
    void offeredOnSignIn() throws Exception {
        api.anonymous().get("/api/auth/demo").andExpect(jsonPath("$.passwordReset").value(true));
    }

    /** The account as a session signed in now holds it (it does not follow later changes). */
    private static UserAccount copy(UserAccount account) {
        UserAccount copy = new UserAccount();
        copy.setId(account.getId());
        copy.setUsername(account.getUsername());
        copy.setPasswordHash(account.getPasswordHash());
        copy.setRole(account.getRole());
        copy.setEnabled(account.isEnabled());
        copy.setMember(account.getMember());
        return copy;
    }
}
