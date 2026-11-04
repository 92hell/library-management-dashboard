package com.helmifr.library.loan;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.member.Member;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The member's own loan list puts what needs their attention first, then their history. */
@IntegrationTest
@DisplayName("My loans: what needs attention first")
class MyLoansOrderTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final Instant NOW = Instant.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired LoanRepository loans;
    UserAccount siti;
    Member member;

    @BeforeEach
    void setUp() {
        siti = data.customer("Siti");
        member = siti.getMember();
    }

    /** A loan of its own book (so every one gets a free copy), with the given dates. */
    private void loan(String title, LoanStatus status, LocalDate dueDate, LocalDate returnDate, Instant requestedAt,
                      LocalDate readyUntil) {
        LocalDate borrowDate = dueDate == null ? null : dueDate.minusDays(14);
        Loan l = data.loan(data.book(title), member, status, borrowDate, dueDate, returnDate);
        l.setRequestedAt(requestedAt);
        l.setReadyUntil(readyUntil);
        loans.save(l);
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    @Test
    @DisplayName("ready for pickup, overdue, on loan (due soonest first), requests, then history newest first")
    void attentionFirstThenHistory() throws Exception {
        // Recorded out of order, history last, so the record order is not the expected order.
        loan("Due later", LoanStatus.ACTIVE, TODAY.plusDays(10), null, null, null);
        loan("Old return", LoanStatus.RETURNED, TODAY.minusDays(35), TODAY.minusDays(30), null, null);
        loan("Waiting in the queue", LoanStatus.WAITLISTED, null, null, daysAgo(3), null);
        loan("Ready for pickup", LoanStatus.READY, null, null, daysAgo(1), TODAY.plusDays(2));
        loan("Cancelled request", LoanStatus.CANCELLED, null, null, daysAgo(10), null);
        loan("Due soon", LoanStatus.ACTIVE, TODAY.plusDays(2), null, null, null);
        loan("Requested", LoanStatus.PENDING, null, null, NOW.minus(Duration.ofHours(2)), null);
        loan("Overdue", LoanStatus.ACTIVE, TODAY.minusDays(2), null, null, null);
        loan("Expired hold", LoanStatus.EXPIRED, null, null, daysAgo(20), null);
        loan("Declined request", LoanStatus.REJECTED, null, null, daysAgo(1), null);
        loan("Recent return", LoanStatus.RETURNED, TODAY.minusDays(5), TODAY.minusDays(3), daysAgo(25), null);

        api.as(siti).get("/api/my/loans")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].book.title").value(contains(
                        "Ready for pickup",
                        "Overdue",
                        "Due soon",
                        "Due later",
                        // Requests and places in the queue, in the order they were made.
                        "Waiting in the queue",
                        "Requested",
                        // History by return date, else request date, newest first.
                        "Declined request",
                        "Recent return",
                        "Cancelled request",
                        "Expired hold",
                        "Old return")));
    }

    @Test
    @DisplayName("history on the same day lists the newest record first")
    void sameDayHistoryNewestRecordFirst() throws Exception {
        loan("Returned first", LoanStatus.RETURNED, TODAY.minusDays(1), TODAY.minusDays(2), null, null);
        loan("Returned second", LoanStatus.RETURNED, TODAY.minusDays(1), TODAY.minusDays(2), null, null);

        api.as(siti).get("/api/my/loans")
                .andExpect(jsonPath("$[*].book.title").value(contains("Returned second", "Returned first")));
    }
}
