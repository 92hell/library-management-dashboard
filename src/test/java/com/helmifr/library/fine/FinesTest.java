package com.helmifr.library.fine;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Late fees: a book returned after its due date costs 1,000 a day (at most 30,000 per loan).
 * Members owing 20,000 or more cannot borrow until they pay; librarians record payments or waive
 * a fine with a reason.
 */
@IntegrationTest
@DisplayName("Overdue fines")
class FinesTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    UserAccount librarian;
    UserAccount siti;
    Book book;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        siti = data.customer("Siti");
        book = data.book("Bumi Manusia");
    }

    /** Siti borrowed the book and returns it today, {@code daysLate} days after it was due. */
    private Loan returnLate(int daysLate) throws Exception {
        return returnLate(book, daysLate);
    }

    private Loan returnLate(Book title, int daysLate) throws Exception {
        LocalDate due = TODAY.minusDays(daysLate);
        Loan loan = data.activeLoan(title, siti.getMember(), due.minusDays(14), due);
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/return").andExpect(status().isOk());
        return loan;
    }

    private Long onlyFineId() throws Exception {
        String body = api.as(siti).get("/api/my/fines").andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.fines[0].id", Long.class);
    }

    @Test
    @DisplayName("returning a book 4 days late charges 4 x 1,000 and tells the member")
    void lateReturnCharges() throws Exception {
        Loan loan = returnLate(4);

        api.as(siti).get("/api/my/fines")
                .andExpect(jsonPath("$.balance").value(4000))
                .andExpect(jsonPath("$.currency").value("IDR"))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.fines[0].amount").value(4000))
                .andExpect(jsonPath("$.fines[0].daysLate").value(4))
                .andExpect(jsonPath("$.fines[0].status").value("UNPAID"))
                .andExpect(jsonPath("$.fines[0].book.title").value("Bumi Manusia"));
        api.as(siti).get("/api/notifications")
                .andExpect(jsonPath("$.content[0].type").value("FINE_ISSUED"))
                .andExpect(jsonPath("$.content[0].message", containsString("IDR 4,000")));
        api.as(librarian).get("/api/borrowed-books/" + loan.getId())
                .andExpect(jsonPath("$.fine.amount").value(4000))
                .andExpect(jsonPath("$.fine.status").value("UNPAID"));
    }

    @Test
    @DisplayName("a book returned on time costs nothing")
    void onTimeIsFree() throws Exception {
        Loan loan = data.activeLoan(book, siti.getMember(), TODAY.minusDays(14), TODAY);
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/return").andExpect(status().isOk())
                .andExpect(jsonPath("$.fine").doesNotExist());

        api.as(siti).get("/api/my/fines")
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.fines").isEmpty());
    }

    @Test
    @DisplayName("a fine is capped at 30,000 per loan")
    void capped() throws Exception {
        returnLate(45);

        api.as(siti).get("/api/my/fines")
                .andExpect(jsonPath("$.fines[0].amount").value(30000))
                .andExpect(jsonPath("$.fines[0].daysLate").value(45));
    }

    @Test
    @DisplayName("returning by barcode charges the same late fee")
    void returnByBarcode() throws Exception {
        LocalDate due = TODAY.minusDays(2);
        Loan loan = data.activeLoan(book, siti.getMember(), due.minusDays(14), due);

        api.as(librarian).post("/api/borrowed-books/return-by-barcode",
                        "{\"barcode\": \"%s\"}".formatted(data.reload(loan).getCopy().getBarcode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fine.amount").value(2000));
    }

    @Test
    @DisplayName("a book still out past its due date shows the fee so far, not yet owed")
    void accruing() throws Exception {
        data.activeLoan(book, siti.getMember(), TODAY.minusDays(20), TODAY.minusDays(6));

        api.as(siti).get("/api/my/loans")
                .andExpect(jsonPath("$[0].fine.amount").value(6000))
                .andExpect(jsonPath("$[0].fine.status").value("ACCRUING"));
        api.as(siti).get("/api/my/fines").andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("a member owing 20,000 or more cannot request, borrow at the desk or collect a hold until they pay")
    void unpaidFinesBlockBorrowing() throws Exception {
        returnLate(data.book("Laskar Pelangi"), 25);
        Book other = data.book("Supernova");

        api.as(siti).get("/api/my/fines").andExpect(jsonPath("$.blocked").value(true));
        api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(other.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("IDR 25,000")));
        api.as(librarian).post("/api/borrowed-books",
                        "{\"bookId\": %d, \"memberId\": %d}".formatted(other.getId(), siti.getMember().getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("unpaid fines")));
        Loan ready = data.loan(book, siti.getMember(), com.helmifr.library.loan.LoanStatus.READY, null, null, null);
        ready.setCopy(data.copy(book));
        api.as(librarian).post("/api/borrowed-books/" + ready.getId() + "/checkout")
                .andExpect(status().isConflict());

        api.as(librarian).post("/api/fines/" + onlyFineId() + "/pay").andExpect(status().isOk());

        api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(other.getId()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("small unpaid fines do not block borrowing")
    void smallFinesDoNotBlock() throws Exception {
        returnLate(3);

        api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(data.book("Supernova").getId()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a librarian records a payment once; the member's balance is cleared")
    void pay() throws Exception {
        returnLate(4);
        Long id = onlyFineId();

        api.as(librarian).post("/api/fines/" + id + "/pay")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.settledBy").value(librarian.getUsername()))
                .andExpect(jsonPath("$.settledAt").isNotEmpty());
        api.as(librarian).post("/api/fines/" + id + "/pay").andExpect(status().isConflict());
        api.as(siti).get("/api/my/fines")
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.fines[0].status").value("PAID"));
    }

    @Test
    @DisplayName("waiving a fine needs a reason, which is kept")
    void waive() throws Exception {
        returnLate(4);
        Long id = onlyFineId();

        api.as(librarian).post("/api/fines/" + id + "/waive", "{\"reason\": \" \"}")
                .andExpect(status().isBadRequest());
        api.as(librarian).post("/api/fines/" + id + "/waive", "{\"reason\": \"Library was closed for Lebaran\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIVED"))
                .andExpect(jsonPath("$.note").value("Library was closed for Lebaran"));
        api.as(librarian).post("/api/fines/" + id + "/pay").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("librarians list fines by status and search by member name or card ID")
    void listAndSearch() throws Exception {
        returnLate(4);
        Long paid = onlyFineId();
        api.as(librarian).post("/api/fines/" + paid + "/pay").andExpect(status().isOk());
        returnLate(data.book("Supernova"), 2);

        api.as(librarian).get("/api/fines", "status", "UNPAID")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(2000))
                .andExpect(jsonPath("$.content[0].member.name").value(siti.getMember().getName()))
                .andExpect(jsonPath("$.content[0].libraryCardId").value(siti.getMember().getLibraryCardId()));
        api.as(librarian).get("/api/fines").andExpect(jsonPath("$.totalElements").value(2));
        api.as(librarian).get("/api/fines", "q", siti.getMember().getLibraryCardId())
                .andExpect(jsonPath("$.totalElements").value(2));
        api.as(librarian).get("/api/fines", "q", "nobody-like-this").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("customers see only their own fines and cannot settle them")
    void customersCannotSettle() throws Exception {
        returnLate(4);
        Long id = onlyFineId();
        UserAccount bayu = data.customer("Bayu");

        api.as(bayu).get("/api/my/fines").andExpect(jsonPath("$.fines").isEmpty());
        api.as(siti).get("/api/fines").andExpect(status().isForbidden());
        api.as(siti).post("/api/fines/" + id + "/pay").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the librarian dashboard shows the total of unpaid fines")
    void dashboardTotal() throws Exception {
        returnLate(4);
        returnLate(data.book("Supernova"), 2);

        api.as(librarian).get("/api/dashboard").andExpect(jsonPath("$.unpaidFines").value(6000));
    }
}
