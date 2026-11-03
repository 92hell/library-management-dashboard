package com.helmifr.library.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@DisplayName("Members API (librarian only)")
class MemberApiTest {

    private static final String VALID_MEMBER = """
            {"name": "Budi Santoso", "dateOfBirth": "1990-05-17", "email": "Budi@Example.com",
             "phone": "081234567890", "active": true}""";

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired UserAccountRepository accounts;
    UserAccount librarian;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
    }

    @Test
    @DisplayName("a walk-in member gets a member code and no online account")
    void createWalkInMember() throws Exception {
        api.as(librarian).post("/api/members", VALID_MEMBER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCode", startsWith("LIB-")))
                .andExpect(jsonPath("$.email").value("budi@example.com"))
                .andExpect(jsonPath("$.accountUsername").doesNotExist());
    }

    @Test
    @DisplayName("regression: updating a member keeps their loans (old mapping deleted them)")
    void updateKeepsLoans() throws Exception {
        Member member = data.member("Siti");
        data.activeLoan(data.book("Bumi Manusia"), member, LocalDate.now(), LocalDate.now().plusDays(14));

        api.as(librarian).put("/api/members/" + member.getId(), VALID_MEMBER).andExpect(status().isOk());

        assertThat(data.loanCount()).isEqualTo(1);
        api.as(librarian).get("/api/members/" + member.getId() + "/loans").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("email and phone are validated")
    void validation() throws Exception {
        api.as(librarian).post("/api/members", """
                        {"name": "A", "dateOfBirth": "1990-01-01", "email": "not-an-email", "phone": "abc"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    @DisplayName("a member with an active loan cannot be deleted (409)")
    void cannotDeleteWithOpenLoan() throws Exception {
        Member member = data.member("Rina");
        data.activeLoan(data.book("Ronggeng"), member, LocalDate.now(), LocalDate.now().plusDays(14));

        api.as(librarian).delete("/api/members/" + member.getId()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("deleting a member also disables their online account")
    void deleteDisablesAccount() throws Exception {
        UserAccount customer = data.customer("Dewi");

        api.as(librarian).delete("/api/members/" + customer.getMember().getId()).andExpect(status().isNoContent());

        assertThat(accounts.findById(customer.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("the member list shows which members have an online account")
    void showsLinkedAccount() throws Exception {
        UserAccount customer = data.customer("Dewi");

        api.as(librarian).get("/api/members/" + customer.getMember().getId())
                .andExpect(jsonPath("$.accountUsername").value(customer.getUsername()));
    }
}
