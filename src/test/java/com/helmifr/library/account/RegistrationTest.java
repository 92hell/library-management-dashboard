package com.helmifr.library.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.member.Member;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@DisplayName("Customer self-registration")
class RegistrationTest {

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired UserAccountRepository accounts;

    private static String registration(String username, String memberCode, String dateOfBirth) {
        return """
                {"username": "%s", "password": "a-long-password", "name": "Rina Wijaya",
                 "email": "rina@example.com", "phone": "081234567890", "dateOfBirth": "%s"%s}"""
                .formatted(username, dateOfBirth, memberCode == null ? "" : ", \"memberCode\": \"" + memberCode + "\"");
    }

    @Test
    @DisplayName("a new customer gets a CUSTOMER account and a new member record")
    void registerNewCustomer() throws Exception {
        api.anonymous().post("/api/auth/register", registration("rina", null, "1995-03-04"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("rina"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.memberId").isNumber());

        UserAccount account = accounts.findByUsernameIgnoreCase("rina").orElseThrow();
        assertThat(account.getMember().getMemberCode()).startsWith("LIB-");
        assertThat(account.getPasswordHash()).doesNotContain("a-long-password");
    }

    @Test
    @DisplayName("a walk-in member links their existing record (and loan history) with member code + birth date")
    void linkExistingWalkInMember() throws Exception {
        Member walkIn = data.member("Rina Wijaya");
        data.activeLoan(data.book("Saman"), walkIn, LocalDate.now(), LocalDate.now().plusDays(14));

        api.anonymous().post("/api/auth/register", registration("rina", walkIn.getMemberCode(), "1990-05-17"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(walkIn.getId()));
    }

    @Test
    @DisplayName("linking fails with a generic message when code and birth date do not match")
    void linkWithWrongBirthDate() throws Exception {
        Member walkIn = data.member("Rina Wijaya");

        api.anonymous().post("/api/auth/register", registration("rina", walkIn.getMemberCode(), "2000-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.memberCode").value("No membership matches this code and date of birth"));
    }

    @Test
    @DisplayName("a member record that already has an account cannot be linked again")
    void cannotLinkTwice() throws Exception {
        UserAccount existing = data.customer("Rina Wijaya");

        api.anonymous().post("/api/auth/register",
                        registration("rina2", existing.getMember().getMemberCode(), "1990-05-17"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.memberCode").exists());
    }

    @Test
    @DisplayName("usernames are unique (case-insensitive) and passwords need 10+ characters")
    void validation() throws Exception {
        api.anonymous().post("/api/auth/register", registration("rina", null, "1995-03-04")).andExpect(status().isCreated());
        api.anonymous().post("/api/auth/register", registration("RINA", null, "1995-03-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
        api.anonymous().post("/api/auth/register", registration("bad name!", null, "1995-03-04")
                        .replace("a-long-password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password", startsWith("size must be")));
    }
}
