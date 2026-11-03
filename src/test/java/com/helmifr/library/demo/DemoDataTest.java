package com.helmifr.library.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.helmifr.library.account.Role;
import com.helmifr.library.account.UserAccountRepository;
import com.helmifr.library.book.BookRepository;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@DisplayName("Demo data")
class DemoDataTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired UserAccountRepository accounts;
    @Autowired BookRepository books;
    @Autowired Api api;

    @Test
    @DisplayName("seeding creates demo accounts, a sample catalogue and loans with their history, and is idempotent")
    void seed() {
        seeder.seed();
        long bookCount = books.count();
        seeder.seed();

        assertThat(bookCount).isGreaterThanOrEqualTo(5);
        assertThat(books.count()).isEqualTo(bookCount);
        assertThat(accounts.findByUsernameIgnoreCase(DemoDataSeeder.DEMO_CUSTOMER).orElseThrow().getRole())
                .isEqualTo(Role.CUSTOMER);
        assertThat(accounts.findByUsernameIgnoreCase(DemoDataSeeder.DEMO_LIBRARIAN).orElseThrow().getRole())
                .isEqualTo(Role.LIBRARIAN);
        // Sample books have valid ISBN-13s (so they get covers), stored the way the app stores them.
        assertThat(books.findAll()).allSatisfy(b -> assertThat(com.helmifr.library.book.Isbn.toIsbn13(b.getIsbn()))
                .contains(b.getIsbn()));
        // Every sample loan has a history, so its receipt shows a real timeline.
        assertThat(loans.findAll()).isNotEmpty().allSatisfy(loan ->
                assertThat(loanEvents.findAllByLoanIdOrderByIdAsc(loan.getId())).isNotEmpty());
    }

    @Autowired com.helmifr.library.loan.LoanRepository loans;
    @Autowired com.helmifr.library.loan.LoanEventRepository loanEvents;

    @Test
    @DisplayName("demo credentials are only advertised when demo mode is on (off in tests)")
    void demoEndpointOffByDefault() throws Exception {
        api.anonymous().get("/api/auth/demo").andExpect(jsonPath("$.enabled").value(false));
    }
}
