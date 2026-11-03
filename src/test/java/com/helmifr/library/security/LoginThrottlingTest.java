package com.helmifr.library.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Login throttling and audit trail")
class LoginThrottlingTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(r -> { r.setRemoteAddr("10.9.9.9"); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }

    @Test
    @DisplayName("after 5 failures from one address, even the right password is refused (429)")
    void lockout() throws Exception {
        UserAccount customer = data.customer("Siti");
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            login(customer.getUsername(), "wrong")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        }
        login(customer.getUsername(), TestData.PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("failed and successful logins are written to the audit log (never the password)")
    void auditTrail(CapturedOutput output) throws Exception {
        UserAccount customer = data.customer("Siti");
        login(customer.getUsername(), "wrong-secret");
        login(customer.getUsername(), TestData.PASSWORD);

        org.assertj.core.api.Assertions.assertThat(output.getOut())
                .contains("AUDIT", "LOGIN_FAILED", "LOGIN_SUCCEEDED", customer.getUsername())
                .doesNotContain("wrong-secret", TestData.PASSWORD);
    }

    @Test
    @DisplayName("a username with line breaks cannot forge extra audit-log lines")
    void noLogInjection(CapturedOutput output) throws Exception {
        // "\\n" in Java is the two characters \ n, which JSON decodes into a real line break.
        login("x\\nFORGED-LINE AUDIT : LOGIN_SUCCEEDED username=librarian", "whatever");

        org.assertj.core.api.Assertions.assertThat(output.getOut()).doesNotContain("\nFORGED-LINE");
    }
}
