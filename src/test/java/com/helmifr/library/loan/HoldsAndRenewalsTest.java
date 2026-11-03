package com.helmifr.library.loan;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.book.CopyRepository;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The holds shelf: an approved request gets a copy set aside (READY, collect within 3 days) or
 * joins the queue (WAITLISTED); a returned copy goes to the next in line; loans renew once.
 */
@IntegrationTest
@DisplayName("Holds queue and renewals")
class HoldsAndRenewalsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired CopyRepository copies;
    @Autowired LoanReminderJob jobs;
    UserAccount librarian;
    UserAccount siti;
    UserAccount bayu;
    Book book;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        siti = data.customer("Siti");
        bayu = data.customer("Bayu");
        book = data.book("Bumi Manusia");
    }

    private Loan request(UserAccount customer) {
        return data.pendingRequest(book, customer.getMember());
    }

    private void approve(Loan loan) throws Exception {
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/approve", "{}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("approval with a free copy sets it aside for 3 days; handing it over starts the loan")
    void readyThenHandOver() throws Exception {
        Loan loan = request(siti);

        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/approve", "{}")
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.readyUntil").value(TODAY.plusDays(3).toString()))
                .andExpect(jsonPath("$.copy.barcode").isNotEmpty());
        api.as(siti).get("/api/notifications").andExpect(jsonPath("$.content[0].message", containsString("ready for pickup")));
        api.as(librarian).get("/api/books/" + book.getId()).andExpect(jsonPath("$.availableCopies").value(0));

        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/checkout")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.borrowDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.dueDate").value(TODAY.plusDays(14).toString()));
    }

    @Test
    @DisplayName("approval without a free copy puts the request in the queue, in request order")
    void waitlistInOrder() throws Exception {
        data.activeLoan(book, data.member("Walk-in"), TODAY, TODAY.plusDays(14));
        Loan first = request(siti);
        Loan second = request(bayu);

        approve(first);
        approve(second);

        api.as(siti).get("/api/my/loans")
                .andExpect(jsonPath("$[0].status").value("WAITLISTED"))
                .andExpect(jsonPath("$[0].queuePosition").value(1));
        api.as(bayu).get("/api/my/loans").andExpect(jsonPath("$[0].queuePosition").value(2));
    }

    @Test
    @DisplayName("a returned copy goes straight to the first in the queue, who is notified")
    void returnPromotesQueue() throws Exception {
        Loan lent = data.activeLoan(book, data.member("Walk-in"), TODAY.minusDays(5), TODAY.plusDays(9));
        Loan first = request(siti);
        Loan second = request(bayu);
        approve(first);
        approve(second);

        api.as(librarian).post("/api/borrowed-books/" + lent.getId() + "/return").andExpect(status().isOk());

        api.as(siti).get("/api/my/loans")
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].readyUntil").value(TODAY.plusDays(3).toString()));
        api.as(siti).get("/api/notifications").andExpect(jsonPath("$.content[0].type").value("HOLD_READY"));
        api.as(bayu).get("/api/my/loans").andExpect(jsonPath("$[0].queuePosition").value(1));
    }

    @Test
    @DisplayName("a copy set aside cannot be lent to someone else at the desk")
    void heldCopyNotLentAtDesk() throws Exception {
        approve(request(siti));

        api.as(librarian).post("/api/borrowed-books",
                        "{\"bookId\": %d, \"memberId\": %d}".formatted(book.getId(), data.member("Ani").getId()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cancelling a hold that is ready passes the copy to the next in the queue")
    void cancelReadyPromotesNext() throws Exception {
        Loan first = request(siti);
        Loan second = request(bayu);
        approve(first);
        approve(second);

        api.as(siti).post("/api/my/loans/" + first.getId() + "/cancel").andExpect(jsonPath("$.status").value("CANCELLED"));

        api.as(bayu).get("/api/my/loans").andExpect(jsonPath("$[0].status").value("READY"));
    }

    @Test
    @DisplayName("an uncollected hold expires after its pickup date and the copy moves on")
    void uncollectedHoldExpires() throws Exception {
        Loan first = request(siti);
        Loan second = request(bayu);
        approve(first);
        approve(second);

        jobs.expireUncollectedHolds(TODAY.plusDays(4));

        api.as(siti).get("/api/my/loans").andExpect(jsonPath("$[0].status").value("EXPIRED"));
        api.as(siti).get("/api/notifications").andExpect(jsonPath("$.content[0].type").value("HOLD_EXPIRED"));
        api.as(bayu).get("/api/my/loans").andExpect(jsonPath("$[0].status").value("READY"));
    }

    @Test
    @DisplayName("adding a copy serves the queue at once")
    void newCopyServesQueue() throws Exception {
        data.activeLoan(book, data.member("Walk-in"), TODAY, TODAY.plusDays(14));
        Loan waiting = request(siti);
        approve(waiting);

        api.as(librarian).post("/api/books/" + book.getId() + "/copies", "{}").andExpect(status().isCreated());

        api.as(siti).get("/api/my/loans").andExpect(jsonPath("$[0].status").value("READY"));
    }

    @Test
    @DisplayName("customers renew once, for another loan period, only if nobody is waiting and it is not overdue")
    void renewals() throws Exception {
        Loan mine = data.activeLoan(book, siti.getMember(), TODAY.minusDays(10), TODAY.plusDays(4));
        String renew = "/api/my/loans/" + mine.getId() + "/renew";

        api.as(siti).get("/api/my/loans").andExpect(jsonPath("$[0].canRenew").value(true));
        api.as(bayu).post(renew).andExpect(status().isNotFound());
        api.as(siti).post(renew)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(TODAY.plusDays(18).toString()))
                .andExpect(jsonPath("$.renewals").value(1));
        api.as(siti).post(renew)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This loan has already been renewed the maximum number of times."));

        Book other = data.book("Saman");
        Loan waitedFor = data.activeLoan(other, siti.getMember(), TODAY.minusDays(3), TODAY.plusDays(11));
        data.pendingRequest(other, bayu.getMember());
        api.as(siti).post("/api/my/loans/" + waitedFor.getId() + "/renew")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Someone is waiting for this book, so it cannot be renewed."));

        Loan late = data.activeLoan(data.book("Late"), siti.getMember(), TODAY.minusDays(20), TODAY.minusDays(1));
        api.as(siti).post("/api/my/loans/" + late.getId() + "/renew")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Overdue loans cannot be renewed; please return the book."));
    }

    @Test
    @DisplayName("librarians can decline a queued request; the queue filter lists it")
    void declineQueued() throws Exception {
        data.activeLoan(book, data.member("Walk-in"), TODAY, TODAY.plusDays(14));
        Loan waiting = request(siti);
        approve(waiting);

        api.as(librarian).get("/api/borrowed-books", "status", "waitlisted").andExpect(jsonPath("$.totalElements").value(1));
        api.as(librarian).post("/api/borrowed-books/" + waiting.getId() + "/reject", "{\"reason\": \"Reference only\"}")
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
