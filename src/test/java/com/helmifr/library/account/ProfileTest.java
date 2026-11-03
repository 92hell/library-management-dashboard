package com.helmifr.library.account;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
@DisplayName("Own profile")
class ProfileTest {

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("customers see their account and library card details")
    void customerProfile() throws Exception {
        UserAccount customer = data.customer("Siti");

        api.as(customer).get("/api/profile")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.member.memberCode").value(customer.getMember().getMemberCode()));
    }

    @Test
    @DisplayName("customers updating their profile also update their member record")
    void updateCustomerProfile() throws Exception {
        UserAccount customer = data.customer("Siti");

        api.as(customer).put("/api/profile", """
                        {"displayName": "Siti Nurhaliza", "email": "siti.n@example.com", "phone": "0811111111"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Siti Nurhaliza"))
                .andExpect(jsonPath("$.member.name").value("Siti Nurhaliza"))
                .andExpect(jsonPath("$.member.phone").value("0811111111"));
    }

    @Test
    @DisplayName("librarians have a profile without a library card")
    void librarianProfile() throws Exception {
        api.as(data.librarian("Lina")).get("/api/profile")
                .andExpect(jsonPath("$.role").value("LIBRARIAN"))
                .andExpect(jsonPath("$.member").doesNotExist());
    }

    @Test
    @DisplayName("changing the password requires the current password")
    void changePassword() throws Exception {
        UserAccount customer = data.customer("Siti");

        api.as(customer).put("/api/profile/password", """
                        {"currentPassword": "wrong-password", "newPassword": "a-brand-new-password"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists());
        api.as(customer).put("/api/profile/password", """
                        {"currentPassword": "%s", "newPassword": "a-brand-new-password"}""".formatted(TestData.PASSWORD))
                .andExpect(status().isNoContent());

        String hash = accounts.findById(customer.getId()).orElseThrow().getPasswordHash();
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("a-brand-new-password", hash)).isTrue();
    }
}
