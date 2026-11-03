package com.helmifr.library.account;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@DisplayName("User administration (librarian only)")
class StaffAccountsTest {

    @Autowired Api api;
    @Autowired TestData data;
    UserAccount librarian;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
    }

    @Test
    @DisplayName("librarians can create other librarian accounts")
    void createLibrarian() throws Exception {
        api.as(librarian).post("/api/users/librarians", """
                        {"username": "budi", "password": "a-long-password", "displayName": "Budi", "email": "budi@library.test"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("LIBRARIAN"));
        api.as(librarian).get("/api/users", "role", "LIBRARIAN", "q", "budi").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("an account can be disabled, but not your own")
    void disable() throws Exception {
        UserAccount customer = data.customer("Siti");

        api.as(librarian).put("/api/users/" + customer.getId() + "/enabled", "{\"enabled\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        api.as(librarian).put("/api/users/" + librarian.getId() + "/enabled", "{\"enabled\": false}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("customers cannot manage accounts")
    void customersForbidden() throws Exception {
        api.as(data.customer("Siti")).get("/api/users").andExpect(status().isForbidden());
    }
}
