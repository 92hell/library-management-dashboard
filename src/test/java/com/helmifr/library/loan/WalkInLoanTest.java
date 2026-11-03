package com.helmifr.library.loan;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.member.Member;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A librarian records a loan at the desk for a member who is physically there. */
@IntegrationTest
@DisplayName("Walk-in loans recorded by a librarian")
class WalkInLoanTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    UserAccount librarian;
    Book book;
    Member member;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        book = data.book("Cantik Itu Luka");
        member = data.member("Ani");
    }

    private String loanJson(Member m) {
        return "{\"bookId\": %d, \"memberId\": %d}".formatted(book.getId(), m.getId());
    }

    @Test
    @DisplayName("defaults: borrowed today, due after the loan period (14 days), status ACTIVE")
    void defaults() throws Exception {
        api.as(librarian).post("/api/borrowed-books", loanJson(member))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.borrowDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.dueDate").value(TODAY.plusDays(14).toString()))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    @DisplayName("works for walk-in members without an online account")
    void walkInMemberWithoutAccount() throws Exception {
        api.as(librarian).post("/api/borrowed-books", loanJson(member)).andExpect(status().isCreated());
        api.as(librarian).get("/api/members/" + member.getId() + "/loans").andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("a book cannot be lent twice until it is returned")
    void noDoubleLending() throws Exception {
        String location = api.as(librarian).post("/api/borrowed-books", loanJson(member))
                .andReturn().getResponse().getHeader("Location");

        api.as(librarian).post("/api/borrowed-books", loanJson(data.member("Bayu"))).andExpect(status().isConflict());

        api.as(librarian).post(location + "/return")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.returnDate").value(TODAY.toString()));
        api.as(librarian).post("/api/borrowed-books", loanJson(data.member("Bayu"))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a past loan can be recorded with its return date (status RETURNED)")
    void recordHistoricalLoan() throws Exception {
        api.as(librarian).post("/api/borrowed-books", """
                        {"bookId": %d, "memberId": %d, "borrowDate": "2024-01-02", "returnDate": "2024-01-10"}"""
                        .formatted(book.getId(), member.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RETURNED"));
    }

    @Test
    @DisplayName("date rules: no future borrow date, return and due dates not before the borrow date")
    void dateRules() throws Exception {
        String base = "{\"bookId\": %d, \"memberId\": %d, ".formatted(book.getId(), member.getId());

        api.as(librarian).post("/api/borrowed-books", base + "\"borrowDate\": \"2024-05-10\", \"returnDate\": \"2024-05-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.returnDate").exists());
        api.as(librarian).post("/api/borrowed-books", base + "\"borrowDate\": \"2024-05-10\", \"dueDate\": \"2024-05-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dueDate").exists());
        api.as(librarian).post("/api/borrowed-books", base + "\"borrowDate\": \"" + TODAY.plusDays(5) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.borrowDate").exists());
    }

    @Test
    @DisplayName("deleted books cannot be lent (404) and inactive members cannot borrow (400)")
    void deletedOrInactive() throws Exception {
        api.as(librarian).delete("/api/books/" + book.getId()).andExpect(status().isNoContent());
        api.as(librarian).post("/api/borrowed-books", loanJson(member)).andExpect(status().isNotFound());

        book = data.book("Another");
        member.setActive(false);
        api.as(librarian).post("/api/borrowed-books", loanJson(member))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.memberId").exists());
    }

    @Test
    @DisplayName("the list filters by status, including derived OVERDUE")
    void statusFilters() throws Exception {
        data.activeLoan(book, member, TODAY.minusDays(20), TODAY.minusDays(6));
        data.activeLoan(data.book("On time"), member, TODAY, TODAY.plusDays(14));
        data.pendingRequest(data.book("Wanted"), member);

        api.as(librarian).get("/api/borrowed-books", "status", "overdue")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].overdue").value(true));
        api.as(librarian).get("/api/borrowed-books", "status", "active").andExpect(jsonPath("$.totalElements").value(2));
        api.as(librarian).get("/api/borrowed-books", "status", "pending").andExpect(jsonPath("$.totalElements").value(1));
        api.as(librarian).get("/api/borrowed-books").andExpect(jsonPath("$.totalElements").value(3));
    }
}
