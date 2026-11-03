package com.helmifr.library.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.book.BookRepository;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/**
 * Demo mode publishes the demo logins, so those accounts must not be able to take over the
 * instance. (Security review finding: the demo librarian could create librarians, disable every
 * other account and lock the owner out.) Runs on its own in-memory database.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "library.demo.enabled=true",
        "spring.datasource.url=${test.isolated-db.prefix}demo${test.isolated-db.suffix}"})
@DisplayName("Demo mode is safe to publish")
class DemoModeTest {

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired UserAccountRepository accounts;
    @Autowired BookRepository books;
    @Autowired DemoDataSeeder seeder;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DemoProperties demo;

    UserAccount demoLibrarian;
    UserAccount demoCustomer;

    @BeforeEach
    void setUp() {
        demoLibrarian = accounts.findByUsernameIgnoreCase(DemoDataSeeder.DEMO_LIBRARIAN).orElseThrow();
        demoCustomer = accounts.findByUsernameIgnoreCase(DemoDataSeeder.DEMO_CUSTOMER).orElseThrow();
    }

    @Test
    @DisplayName("the demo librarian cannot create librarians or disable accounts")
    void demoLibrarianCannotAdministerAccounts() throws Exception {
        UserAccount owner = accounts.findByUsernameIgnoreCase("librarian").orElseThrow();

        api.as(demoLibrarian).post("/api/users/librarians", """
                        {"username": "intruder", "password": "a-long-password", "displayName": "X"}""")
                .andExpect(status().isForbidden());
        api.as(demoLibrarian).put("/api/users/" + owner.getId() + "/enabled", "{\"enabled\": false}")
                .andExpect(status().isForbidden());
        api.as(demoLibrarian).get("/api/users").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("demo accounts cannot change their password or profile")
    void demoAccountsCannotChangeCredentials() throws Exception {
        String change = "{\"currentPassword\": \"%s\", \"newPassword\": \"another-password\"}".formatted(demo.password());
        api.as(demoLibrarian).put("/api/profile/password", change).andExpect(status().isForbidden());
        api.as(demoCustomer).put("/api/profile/password", change).andExpect(status().isForbidden());
        api.as(demoCustomer).put("/api/profile", """
                        {"displayName": "Hacked", "email": "x@example.com"}""")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the demo librarian can still run the desk (catalogue, loans)")
    void demoLibrarianCanRunTheDesk() throws Exception {
        api.as(demoLibrarian).get("/api/borrowed-books").andExpect(status().isOk());
        api.as(demoLibrarian).post("/api/authors", "{\"name\": \"Demo Author\"}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the session tells the UI which accounts are restricted demo accounts")
    void sessionFlagsDemoAccounts() throws Exception {
        api.as(demoLibrarian).get("/api/auth/me").andExpect(jsonPath("$.demoAccount").value(true));
        api.as(data.librarian("Real Staff")).get("/api/auth/me").andExpect(jsonPath("$.demoAccount").value(false));
    }

    @Test
    @DisplayName("registration is off, so no real personal data ends up on a public demo")
    void registrationDisabled() throws Exception {
        api.anonymous().post("/api/auth/register", """
                        {"username": "rina", "password": "a-long-password", "name": "Rina", "email": "rina@example.com",
                         "phone": "0899999999", "dateOfBirth": "1995-03-04"}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Registration is turned off in this demo."));
        api.anonymous().get("/api/auth/demo")
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.registrationOpen").value(false));
    }

    @Test
    @DisplayName("the nightly reset restores demo accounts and sample data, keeping the owner's account")
    void resetRestoresTheDemo() {
        data.book("Vandalised entry");
        demoCustomer.setEnabled(false);
        demoLibrarian.setPasswordHash(passwordEncoder.encode("changed-by-a-visitor"));
        long sampleBooks = books.count() - 1;

        seeder.reset();

        assertThat(books.count()).isEqualTo(sampleBooks);
        UserAccount customer = accounts.findByUsernameIgnoreCase(DemoDataSeeder.DEMO_CUSTOMER).orElseThrow();
        UserAccount librarian = accounts.findByUsernameIgnoreCase(DemoDataSeeder.DEMO_LIBRARIAN).orElseThrow();
        assertThat(customer.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches(demo.password(), librarian.getPasswordHash())).isTrue();
        assertThat(accounts.findByUsernameIgnoreCase("librarian")).isPresent();
    }

    @Test
    @DisplayName("the reset also clears loan histories and receipts that visitors created")
    void resetClearsHistoryAndReceipts() throws Exception {
        var book = data.book("Visitor's book");
        String created = api.as(demoLibrarian).post("/api/borrowed-books",
                        "{\"bookId\": %d, \"memberId\": %d}".formatted(book.getId(), demoCustomer.getMember().getId()))
                .andReturn().getResponse().getContentAsString();
        long loanId = com.jayway.jsonpath.JsonPath.parse(created).read("$.id", Long.class);
        api.as(demoLibrarian).post("/api/borrowed-books/" + loanId + "/receipt")
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        seeder.reset();

        assertThat(receipts.count()).isZero();
        assertThat(loanEvents.findAllByLoanIdOrderByIdAsc(loanId)).isEmpty();
    }

    @Autowired com.helmifr.library.receipt.ReceiptRepository receipts;
    @Autowired com.helmifr.library.loan.LoanEventRepository loanEvents;
}
