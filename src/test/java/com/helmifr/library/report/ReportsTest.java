package com.helmifr.library.report;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.member.Member;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Librarian reports over a date range (default: the last 30 days): lending totals, the late-return
 * rate, most borrowed titles, most active members, fines; and CSV downloads of the same data.
 */
@IntegrationTest
@DisplayName("Reports and CSV export")
class ReportsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    UserAccount librarian;
    Member siti;
    Member bayu;
    Book laskar;
    Book bumi;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        siti = data.member("Siti");
        bayu = data.member("Bayu");
        laskar = data.bookWithCopies("Laskar Pelangi", 3);
        bumi = data.book("Bumi Manusia");
    }

    /** A loan borrowed {@code daysAgo} days ago, due 14 days later, returned {@code returnedAgo} days ago (or still out). */
    private void loan(Book book, Member member, int daysAgo, Integer returnedAgo) {
        LocalDate borrowed = TODAY.minusDays(daysAgo);
        LocalDate returned = returnedAgo == null ? null : TODAY.minusDays(returnedAgo);
        data.loan(book, member, returned == null ? LoanStatus.ACTIVE : LoanStatus.RETURNED, borrowed, borrowed.plusDays(14),
                returned);
    }

    private void typicalMonth() {
        loan(laskar, siti, 25, 15);  // due 11 days ago, back on time
        loan(laskar, bayu, 20, 2);   // due 6 days ago, back 4 days late
        loan(laskar, siti, 18, null); // due 4 days ago, still out: late
        loan(bumi, siti, 3, null);    // not yet due
        loan(bumi, bayu, 60, 50);     // outside the last 30 days
    }

    @Test
    @DisplayName("the summary counts loans, returns, late returns and active members in the last 30 days")
    void summary() throws Exception {
        typicalMonth();

        api.as(librarian).get("/api/reports/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(TODAY.minusDays(29).toString()))
                .andExpect(jsonPath("$.to").value(TODAY.toString()))
                .andExpect(jsonPath("$.loans").value(4))
                .andExpect(jsonPath("$.returns").value(2))
                .andExpect(jsonPath("$.dueInPeriod").value(3))
                .andExpect(jsonPath("$.lateInPeriod").value(2))
                .andExpect(jsonPath("$.lateRate").value(0.667))
                .andExpect(jsonPath("$.activeMembers").value(2))
                .andExpect(jsonPath("$.newMembers").value(2));
    }

    @Test
    @DisplayName("fines charged and collected in the range are totalled")
    void fines() throws Exception {
        var late = data.loan(laskar, siti, LoanStatus.RETURNED, TODAY.minusDays(20), TODAY.minusDays(6), TODAY.minusDays(2));
        fine(late, 4000, com.helmifr.library.fine.FineStatus.PAID);
        var later = data.loan(bumi, bayu, LoanStatus.RETURNED, TODAY.minusDays(20), TODAY.minusDays(6), TODAY.minusDays(3));
        fine(later, 3000, com.helmifr.library.fine.FineStatus.UNPAID);

        api.as(librarian).get("/api/reports/summary")
                .andExpect(jsonPath("$.finesCharged").value(7000))
                .andExpect(jsonPath("$.finesCollected").value(4000))
                .andExpect(jsonPath("$.currency").value("IDR"));
    }

    @Autowired com.helmifr.library.fine.FineRepository fineRepository;

    private void fine(com.helmifr.library.loan.Loan loan, long amount, com.helmifr.library.fine.FineStatus status)
            throws Exception {
        var f = new com.helmifr.library.fine.Fine();
        f.setLoan(loan);
        f.setMember(loan.getMember());
        f.setAmount(amount);
        f.setDaysLate(4);
        fineRepository.save(f);
        if (status == com.helmifr.library.fine.FineStatus.PAID) {
            // Collected money comes from recorded payments.
            api.as(librarian).post("/api/fines/" + f.getId() + "/pay").andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("the late rate is empty, not zero, when nothing fell due")
    void noLateRateWithoutDueLoans() throws Exception {
        loan(bumi, siti, 3, null);

        api.as(librarian).get("/api/reports/summary")
                .andExpect(jsonPath("$.dueInPeriod").value(0))
                .andExpect(jsonPath("$.lateRate").doesNotExist());
    }

    @Test
    @DisplayName("most borrowed titles and most active members are ranked for the chosen range")
    void rankings() throws Exception {
        typicalMonth();

        api.as(librarian).get("/api/reports/summary", "from", TODAY.minusDays(70).toString(), "to", TODAY.toString())
                .andExpect(jsonPath("$.loans").value(5))
                .andExpect(jsonPath("$.mostBorrowed[0].title").value("Laskar Pelangi"))
                .andExpect(jsonPath("$.mostBorrowed[0].authorName").isNotEmpty())
                .andExpect(jsonPath("$.mostBorrowed[0].loans").value(3))
                .andExpect(jsonPath("$.mostBorrowed[1].title").value("Bumi Manusia"))
                .andExpect(jsonPath("$.mostBorrowed[1].loans").value(2))
                .andExpect(jsonPath("$.mostActiveMembers[0].name").value("Siti"))
                .andExpect(jsonPath("$.mostActiveMembers[0].libraryCardId").value(siti.getLibraryCardId()))
                .andExpect(jsonPath("$.mostActiveMembers[0].loans").value(3))
                .andExpect(jsonPath("$.mostActiveMembers[1].loans").value(2));
    }

    @Test
    @DisplayName("requests, holds and deleted loans are not counted as lending")
    void onlyRealLoansCount() throws Exception {
        data.pendingRequest(bumi, siti);
        data.loan(bumi, bayu, LoanStatus.CANCELLED, null, null, null);
        var deleted = data.activeLoan(laskar, bayu, TODAY.minusDays(1), TODAY.plusDays(13));
        deleted.setDeleted(true);

        api.as(librarian).get("/api/reports/summary").andExpect(jsonPath("$.loans").value(0));
    }

    @Test
    @DisplayName("the loans CSV has a header, one row per loan, quoted fields and no spreadsheet formulas")
    void loansCsv() throws Exception {
        Book tricky = data.book("=HYPERLINK(\"http://evil\",\"Click\")");
        loan(tricky, siti, 2, null);
        loan(laskar, data.member("Rahman, Budi"), 20, 2);

        var response = api.as(librarian).get("/api/reports/loans.csv")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"loans-%s-to-%s.csv\"".formatted(TODAY.minusDays(29), TODAY)))
                .andReturn().getResponse();
        String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThat(csv)
                .startsWith("\uFEFF\"Loan ID\",\"Borrowed\",\"Due\",\"Returned\",\"Status\",\"Days late\",\"Title\","
                        + "\"Author\",\"Copy barcode\",\"Member\",\"Library card ID\"\r\n")
                .contains(",\"Rahman, Budi\",")
                .contains(",4,\"Laskar Pelangi\",")
                .contains("\"'=HYPERLINK(\"\"http://evil\"\",\"\"Click\"\")\"")
                .doesNotContain(",=HYPERLINK");
        org.assertj.core.api.Assertions.assertThat(csv.split("\r\n")).hasSize(3);
    }

    @Test
    @DisplayName("the most-borrowed CSV lists titles with their loan counts")
    void mostBorrowedCsv() throws Exception {
        typicalMonth();

        api.as(librarian).get("/api/reports/most-borrowed.csv")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"Rank\",\"Title\",\"Author\",\"Loans\"\r\n1,\"Laskar Pelangi\",")))
                .andExpect(content().string(containsString("2,\"Bumi Manusia\",")));
    }

    @Test
    @DisplayName("a range that ends before it starts, or is longer than a year, is refused")
    void invalidRange() throws Exception {
        api.as(librarian).get("/api/reports/summary", "from", TODAY.toString(), "to", TODAY.minusDays(1).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.to").exists());
        api.as(librarian).get("/api/reports/loans.csv", "from", TODAY.minusDays(400).toString(), "to", TODAY.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.from").exists());
    }

    @Test
    @DisplayName("reports are for librarians only")
    void librariansOnly() throws Exception {
        UserAccount customer = data.customer("Ani");

        api.as(customer).get("/api/reports/summary").andExpect(status().isForbidden());
        api.as(customer).get("/api/reports/loans.csv").andExpect(status().isForbidden());
    }
}
