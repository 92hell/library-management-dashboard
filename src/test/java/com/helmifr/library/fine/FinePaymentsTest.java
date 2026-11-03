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
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A late fee can be paid in parts: each payment is recorded, the rest stays due (and alone counts
 * towards the borrowing limit), and the loan receipt shows every payment as signed evidence.
 */
@IntegrationTest
@DisplayName("Fine payments (in parts) and payment receipts")
class FinePaymentsTest {

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

    /** Siti returns the book {@code daysLate} days late; returns the fine's id. */
    private long lateFee(int daysLate) throws Exception {
        LocalDate due = TODAY.minusDays(daysLate);
        Loan loan = data.activeLoan(book, siti.getMember(), due.minusDays(14), due);
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/return").andExpect(status().isOk());
        return ((Number) JsonPath.read(api.as(siti).get("/api/my/fines").andReturn().getResponse().getContentAsString(),
                "$.fines[0].id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions pay(long fineId, String amount) throws Exception {
        return api.as(librarian).post("/api/fines/" + fineId + "/pay", "{\"amount\": " + amount + "}");
    }

    @Test
    @DisplayName("paying part of a fee leaves the rest due; paying the rest settles it")
    void inParts() throws Exception {
        long fine = lateFee(3);

        pay(fine, "1000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.paidAmount").value(1000))
                .andExpect(jsonPath("$.outstanding").value(2000))
                .andExpect(jsonPath("$.payments[0].amount").value(1000))
                .andExpect(jsonPath("$.payments[0].recordedBy").value(librarian.getDisplayName()));
        api.as(siti).get("/api/my/fines").andExpect(jsonPath("$.balance").value(2000));

        pay(fine, "2000")
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.outstanding").value(0))
                .andExpect(jsonPath("$.payments.length()").value(2));
        api.as(siti).get("/api/my/fines").andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("without an amount the whole remaining fee is paid")
    void payRest() throws Exception {
        long fine = lateFee(3);
        pay(fine, "500");

        api.as(librarian).post("/api/fines/" + fine + "/pay")
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(3000))
                .andExpect(jsonPath("$.payments[1].amount").value(2500));
    }

    @Test
    @DisplayName("a payment must be more than zero and no more than what is still due")
    void amountChecked() throws Exception {
        long fine = lateFee(3);

        pay(fine, "0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.amount").exists());
        pay(fine, "3001").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount", containsString("IDR 3,000")));
    }

    @Test
    @DisplayName("only what is still due counts towards the borrowing limit")
    void partPaymentUnblocks() throws Exception {
        long fine = lateFee(25);
        Book other = data.book("Supernova");
        api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(other.getId())).andExpect(status().isConflict());

        pay(fine, "10000").andExpect(jsonPath("$.outstanding").value(15000));

        api.as(siti).get("/api/my/fines").andExpect(jsonPath("$.blocked").value(false));
        api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(other.getId())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("waiving after a part payment forgives the rest and keeps the payment on record")
    void waiveRest() throws Exception {
        long fine = lateFee(3);
        pay(fine, "1000");

        api.as(librarian).post("/api/fines/" + fine + "/waive", "{\"reason\": \"Hardship\"}")
                .andExpect(jsonPath("$.status").value("WAIVED"))
                .andExpect(jsonPath("$.paidAmount").value(1000))
                .andExpect(jsonPath("$.outstanding").value(0));
        api.as(siti).get("/api/my/fines").andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("each payment is in the loan's history, and the signed receipt lists the payments and what is still due")
    void paymentReceipt() throws Exception {
        long fine = lateFee(3);
        pay(fine, "1000");
        long loanId = ((Number) JsonPath.read(api.as(librarian).get("/api/fines").andReturn().getResponse()
                .getContentAsString(), "$.content[0].loanId")).longValue();

        api.as(librarian).get("/api/borrowed-books/" + loanId + "/events")
                .andExpect(jsonPath("$[2].type").value("FINE_PAID"))
                .andExpect(jsonPath("$[2].detail").value("Paid IDR 1,000; IDR 2,000 still due"));
        String receipt = api.as(librarian).post("/api/borrowed-books/" + loanId + "/receipt")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat((Integer) JsonPath.read(receipt, "$.content.fine.paid")).isEqualTo(1000);
        org.assertj.core.api.Assertions.assertThat((Integer) JsonPath.read(receipt, "$.content.fine.outstanding")).isEqualTo(2000);
        org.assertj.core.api.Assertions.assertThat((Integer) JsonPath.read(receipt, "$.content.fine.payments[0].amount")).isEqualTo(1000);
        org.assertj.core.api.Assertions.assertThat((Boolean) JsonPath.read(receipt, "$.genuine")).isTrue();
        byte[] pdf = api.as(librarian).get("/api/receipts/" + JsonPath.read(receipt, "$.number") + ".pdf")
                .andReturn().getResponse().getContentAsByteArray();
        String text = new com.lowagie.text.pdf.parser.PdfTextExtractor(new com.lowagie.text.pdf.PdfReader(pdf)).getTextFromPage(1);
        org.assertj.core.api.Assertions.assertThat(text).contains("Paid / Dibayar").contains("IDR 2,000");
    }

    @Test
    @DisplayName("a correction cannot change a fee that is already partly paid")
    void correctionRefusedAfterPartPayment() throws Exception {
        LocalDate due = TODAY.minusDays(3);
        Loan loan = data.activeLoan(data.book("Laskar Pelangi"), siti.getMember(), due.minusDays(14), due);
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/return").andExpect(status().isOk());
        long fine = ((Number) JsonPath.read(api.as(siti).get("/api/my/fines").andReturn().getResponse()
                .getContentAsString(), "$.fines[0].id")).longValue();
        pay(fine, "1000");

        api.as(librarian).put("/api/borrowed-books/" + loan.getId(), """
                        {"bookId": %d, "memberId": %d, "borrowDate": "%s", "dueDate": "%s", "returnDate": "%s"}"""
                        .formatted(loan.getBook().getId(), siti.getMember().getId(), due.minusDays(14), TODAY, TODAY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("partly paid")));
    }
}
