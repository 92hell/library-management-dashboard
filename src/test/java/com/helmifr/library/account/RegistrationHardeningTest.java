package com.helmifr.library.account;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.member.Member;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Registration does not reveal who is a member, and cannot be used to probe or to mass-create
 * accounts: every refusal counts towards the network address's lockout, and successful sign-ups
 * are capped per address.
 */
@IntegrationTest
@DisplayName("Registration hardening (enumeration and sign-up limits)")
class RegistrationHardeningTest {

    private static final AtomicInteger ADDRESS = new AtomicInteger(10);
    private static final AtomicInteger USER = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired TestData data;

    /** A fresh network address per test, so the in-memory limits never carry over between tests. */
    private static String freshAddress() {
        return "198.51.100." + ADDRESS.incrementAndGet();
    }

    private ResultActions register(String address, String username, String email, String phone, String cardId) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .with(r -> { r.setRemoteAddr(address); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "a-long-password", "name": "Rina Wijaya",
                         "email": "%s", "phone": "%s", "dateOfBirth": "1995-03-04"%s}"""
                        .formatted(username, email, phone, cardId == null ? "" : ", \"libraryCardId\": \"" + cardId + "\"")));
    }

    private static String unique(String prefix) {
        return prefix + USER.incrementAndGet() + "x" + System.nanoTime() % 100000;
    }

    @Test
    @DisplayName("a member's email gets the same answer as a wrong card ID: the reply never confirms who is a member")
    void sameAnswerForMemberAndWrongCard() throws Exception {
        Member walkIn = data.member("Siti Rahma");
        String address = freshAddress();

        String byEmail = register(address, unique("probe"), walkIn.getEmail(), "0899999999", null)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String byWrongCard = register(address, unique("probe"), "someone@else.example", "0899999998", "LIB-NOPE00")
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(com.jayway.jsonpath.JsonPath.<String>read(byEmail, "$.errors.libraryCardId"))
                .isEqualTo(RegistrationTest.GENERIC)
                .isEqualTo(com.jayway.jsonpath.JsonPath.<String>read(byWrongCard, "$.errors.libraryCardId"));
    }

    @Test
    @DisplayName("every refused attempt counts: after 5 (taken usernames included), the address must wait")
    void refusalsLockTheAddress() throws Exception {
        UserAccount existing = data.customer("Bayu");
        String address = freshAddress();
        for (int i = 0; i < 5; i++) {
            register(address, existing.getUsername(), unique("free") + "@example.org", "08123456" + (10 + i), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.username").exists());
        }

        register(address, unique("fresh"), unique("fresh") + "@example.org", "0812345699", null)
                .andExpect(status().isTooManyRequests());
        register(freshAddress(), unique("other"), unique("other") + "@example.org", "0812345698", null)
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("at most 3 new accounts per address per hour; another address is not affected")
    void signUpsCapped() throws Exception {
        String address = freshAddress();
        for (int i = 0; i < 3; i++) {
            register(address, unique("rina"), unique("rina") + "@example.org", "08211111" + (10 + i), null)
                    .andExpect(status().isCreated());
        }

        register(address, unique("rina"), unique("rina") + "@example.org", "0821111199", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Too many new accounts from this network. Please try again later."));
        register(freshAddress(), unique("ani"), unique("ani") + "@example.org", "0821111198", null)
                .andExpect(status().isCreated());
    }
}
