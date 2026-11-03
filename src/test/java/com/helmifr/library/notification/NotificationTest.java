package com.helmifr.library.notification;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanReminderJob;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@IntegrationTest
@RecordApplicationEvents
@DisplayName("Notifications")
class NotificationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired LoanReminderJob reminders;
    @Autowired ApplicationEvents events;
    UserAccount librarian;
    UserAccount customer;
    Book book;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        customer = data.customer("Siti");
        book = data.book("Bumi Manusia");
    }

    @Test
    @DisplayName("librarians are notified of a new borrow request")
    void librarianNotifiedOfRequest() throws Exception {
        api.as(customer).post("/api/my/loans", "{\"bookId\": %d}".formatted(book.getId()));

        api.as(librarian).get("/api/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
        api.as(librarian).get("/api/notifications")
                .andExpect(jsonPath("$.content[0].type").value("BORROW_REQUESTED"))
                .andExpect(jsonPath("$.content[0].message", containsString("Bumi Manusia")))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    @DisplayName("customers are notified when their request is approved or rejected")
    void customerNotifiedOfDecision() throws Exception {
        Loan first = data.pendingRequest(book, customer.getMember());
        Loan second = data.pendingRequest(data.book("Saman"), customer.getMember());

        api.as(librarian).post("/api/borrowed-books/" + first.getId() + "/approve", "{}").andExpect(status().isOk());
        api.as(librarian).post("/api/borrowed-books/" + second.getId() + "/reject", "{\"reason\": \"Damaged copy\"}");

        api.as(customer).get("/api/notifications")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.type == 'REQUEST_APPROVED')]").exists())
                .andExpect(jsonPath("$.content[?(@.type == 'REQUEST_REJECTED')].message", org.hamcrest.Matchers.hasItem(containsString("Damaged copy"))));
    }

    @Test
    @DisplayName("customers with an account are notified when a librarian records a walk-in loan for them")
    void customerNotifiedOfWalkInLoan() throws Exception {
        api.as(librarian).post("/api/borrowed-books", "{\"bookId\": %d, \"memberId\": %d}"
                .formatted(book.getId(), customer.getMember().getId()));

        api.as(customer).get("/api/notifications").andExpect(jsonPath("$.content[0].type").value("LOAN_RECORDED"));
    }

    @Test
    @DisplayName("reminder job: 'due soon' within 2 days and 'overdue' after the due date, each sent once")
    void dueReminders() throws Exception {
        data.activeLoan(book, customer.getMember(), TODAY.minusDays(12), TODAY.plusDays(2));
        data.activeLoan(data.book("Late"), customer.getMember(), TODAY.minusDays(20), TODAY.minusDays(1));
        data.activeLoan(data.book("Far away"), customer.getMember(), TODAY, TODAY.plusDays(14));

        reminders.sendReminders(TODAY);
        reminders.sendReminders(TODAY);

        api.as(customer).get("/api/notifications")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.type == 'DUE_SOON')]").exists())
                .andExpect(jsonPath("$.content[?(@.type == 'OVERDUE')]").exists());
    }

    @Test
    @DisplayName("notifications can be marked read one by one or all at once, only by their owner")
    void markRead() throws Exception {
        data.pendingRequest(book, customer.getMember());
        api.as(customer).post("/api/my/loans", "{\"bookId\": %d}".formatted(data.book("Saman").getId()));
        api.as(customer).post("/api/my/loans", "{\"bookId\": %d}".formatted(data.book("Ronggeng").getId()));
        String json = api.as(librarian).get("/api/notifications").andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.parse(json).read("$.content[0].id", Long.class);

        api.as(customer).post("/api/notifications/" + id + "/read").andExpect(status().isNotFound());
        api.as(librarian).post("/api/notifications/" + id + "/read").andExpect(status().isNoContent());
        api.as(librarian).get("/api/notifications/unread-count").andExpect(jsonPath("$.count").value(1));
        api.as(librarian).post("/api/notifications/read-all").andExpect(status().isNoContent());
        api.as(librarian).get("/api/notifications/unread-count").andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("every in-app notification is also published for the external channels (email/SMS/WhatsApp)")
    void publishesForExternalChannels() throws Exception {
        api.as(customer).post("/api/my/loans", "{\"bookId\": %d}".formatted(book.getId()));

        org.assertj.core.api.Assertions.assertThat(events.stream(NotificationCreated.class))
                .anySatisfy(e -> {
                    org.assertj.core.api.Assertions.assertThat(e.accountId()).isEqualTo(librarian.getId());
                    org.assertj.core.api.Assertions.assertThat(e.type()).isEqualTo(NotificationType.BORROW_REQUESTED);
                });
    }
}
