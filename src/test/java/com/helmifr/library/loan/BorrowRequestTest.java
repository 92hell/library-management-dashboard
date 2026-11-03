package com.helmifr.library.loan;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A customer requests a book online; a librarian approves (hands it over) or rejects it. */
@IntegrationTest
@DisplayName("Borrow requests: customer requests, librarian decides")
class BorrowRequestTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    UserAccount librarian;
    UserAccount customer;
    Book book;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        customer = data.customer("Siti");
        book = data.book("Bumi Manusia");
    }

    private String request(Book b) {
        return "{\"bookId\": %d}".formatted(b.getId());
    }

    private String requestAndGetLocation() throws Exception {
        return api.as(customer).post("/api/my/loans", request(book))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }

    @Test
    @DisplayName("a request starts PENDING and shows up in the customer's loans and the librarian queue")
    void requestIsPending() throws Exception {
        api.as(customer).post("/api/my/loans", request(book))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.borrowDate").doesNotExist());

        api.as(customer).get("/api/my/loans").andExpect(jsonPath("$", hasSize(1)));
        api.as(librarian).get("/api/borrowed-books", "status", "pending").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("approving sets a copy aside; handing it over lends it: borrowed today, due in 14 days")
    void approve() throws Exception {
        String loan = requestAndGetLocation().replace("/api/my/loans", "/api/borrowed-books");

        api.as(librarian).post(loan + "/approve", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        api.as(librarian).post(loan + "/checkout")
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.borrowDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.dueDate").value(TODAY.plusDays(14).toString()));
    }

    @Test
    @DisplayName("approving while every copy is lent puts the request in the queue")
    void approveWhileOnLoan() throws Exception {
        String loan = requestAndGetLocation().replace("/api/my/loans", "/api/borrowed-books");
        data.activeLoan(book, data.member("Walk-in"), TODAY, TODAY.plusDays(14));

        api.as(librarian).post(loan + "/approve", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITLISTED"));
    }

    @Test
    @DisplayName("rejecting keeps the reason so the customer can see it")
    void reject() throws Exception {
        String loan = requestAndGetLocation().replace("/api/my/loans", "/api/borrowed-books");

        api.as(librarian).post(loan + "/reject", "{\"reason\": \"Reserved for a class\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        api.as(customer).get("/api/my/loans").andExpect(jsonPath("$[0].decisionNote").value("Reserved for a class"));
    }

    @Test
    @DisplayName("only pending requests can be approved or rejected (409 otherwise)")
    void onlyPendingCanBeDecided() throws Exception {
        Loan active = data.activeLoan(book, customer.getMember(), TODAY, TODAY.plusDays(14));

        api.as(librarian).post("/api/borrowed-books/" + active.getId() + "/approve", "{}").andExpect(status().isConflict());
        api.as(librarian).post("/api/borrowed-books/" + active.getId() + "/reject", "{}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("customers can cancel their own pending request, but not someone else's")
    void cancel() throws Exception {
        String location = requestAndGetLocation();

        api.as(data.customer("Other")).post(location + "/cancel").andExpect(status().isNotFound());
        api.as(customer).post(location + "/cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        api.as(customer).post(location + "/cancel").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a customer cannot request the same book twice while a request or loan is open")
    void noDuplicateRequests() throws Exception {
        requestAndGetLocation();
        api.as(customer).post("/api/my/loans", request(book)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a customer can have at most 3 open requests/loans")
    void openLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            api.as(customer).post("/api/my/loans", request(data.book("Book " + i))).andExpect(status().isCreated());
        }
        api.as(customer).post("/api/my/loans", request(book))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can have at most 3 open requests or loans."));
    }

    @Test
    @DisplayName("customers only ever see their own loans")
    void customersSeeOnlyTheirOwn() throws Exception {
        data.activeLoan(data.book("Someone else's"), data.member("Stranger"), TODAY, TODAY.plusDays(14));

        api.as(customer).get("/api/my/loans").andExpect(jsonPath("$", hasSize(0)));
        api.as(customer).get("/api/borrowed-books").andExpect(status().isForbidden());
    }
}
