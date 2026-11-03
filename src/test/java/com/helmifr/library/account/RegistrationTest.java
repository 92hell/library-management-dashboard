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

/**
 * A walk-in member (card, no online account) becomes a registered customer by linking their
 * library card ID; the email or phone given must match what the library has on file.
 */
@IntegrationTest
@DisplayName("Customer self-registration")
class RegistrationTest {

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired UserAccountRepository accounts;

    private static String registration(String username, String email, String phone, String libraryCardId) {
        return """
                {"username": "%s", "password": "a-long-password", "name": "Rina Wijaya",
                 "email": "%s", "phone": "%s", "dateOfBirth": "1995-03-04"%s}"""
                .formatted(username, email, phone,
                        libraryCardId == null ? "" : ", \"libraryCardId\": \"" + libraryCardId + "\"");
    }

    @Test
    @DisplayName("a new customer gets a CUSTOMER account and a new library card")
    void registerNewCustomer() throws Exception {
        api.anonymous().post("/api/auth/register", registration("rina", "rina@example.com", "0899999999", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("rina"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.memberId").isNumber());

        UserAccount account = accounts.findByUsernameIgnoreCase("rina").orElseThrow();
        assertThat(account.getMember().getLibraryCardId()).startsWith("LIB-");
        assertThat(account.getPasswordHash()).doesNotContain("a-long-password");
    }

    @Test
    @DisplayName("a walk-in member links their card (and loan history) with card ID + the email on file")
    void linkWithCardIdAndEmail() throws Exception {
        Member walkIn = data.member("Rina Wijaya");
        data.activeLoan(data.book("Saman"), walkIn, LocalDate.now(), LocalDate.now().plusDays(14));

        api.anonymous().post("/api/auth/register",
                        registration("rina", walkIn.getEmail(), "0899999999", walkIn.getLibraryCardId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(walkIn.getId()));
    }

    @Test
    @DisplayName("...or with card ID + the phone on file, written in another format (+62 / 0 prefix)")
    void linkWithCardIdAndPhone() throws Exception {
        Member walkIn = data.member("Rina Wijaya"); // phone on file: 081234567890

        api.anonymous().post("/api/auth/register",
                        registration("rina", "new-address@example.com", "+62 812-3456-7890", walkIn.getLibraryCardId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(walkIn.getId()));
    }

    @Test
    @DisplayName("a card ID alone is not enough: neither email nor phone match -> generic error")
    void cardIdWithoutMatchingContact() throws Exception {
        Member walkIn = data.member("Rina Wijaya");

        api.anonymous().post("/api/auth/register",
                        registration("rina", "someone@else.com", "0899999999", walkIn.getLibraryCardId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.libraryCardId")
                        .value("No library card matches this ID with your email or phone number"));
    }

    @Test
    @DisplayName("a card that already has an online account cannot be linked again")
    void cannotLinkTwice() throws Exception {
        UserAccount existing = data.customer("Rina Wijaya");
        Member card = existing.getMember();

        api.anonymous().post("/api/auth/register",
                        registration("rina2", card.getEmail(), card.getPhone(), card.getLibraryCardId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.libraryCardId").exists());
    }

    @Test
    @DisplayName("registering without a card ID, with the email of an unlinked walk-in card, asks for the card ID")
    void preventsDuplicateCards() throws Exception {
        Member walkIn = data.member("Rina Wijaya");

        api.anonymous().post("/api/auth/register", registration("rina", walkIn.getEmail(), "0899999999", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.libraryCardId", startsWith("You already have a library card")));
    }

    @Test
    @DisplayName("usernames are unique (case-insensitive) and passwords need 10+ characters")
    void validation() throws Exception {
        api.anonymous().post("/api/auth/register", registration("rina", "rina@example.com", "0899999999", null))
                .andExpect(status().isCreated());
        api.anonymous().post("/api/auth/register", registration("RINA", "rina2@example.com", "0877777777", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
        api.anonymous().post("/api/auth/register", registration("bad name!", "x@example.com", "0866666666", null)
                        .replace("a-long-password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password", startsWith("size must be")));
    }
}
