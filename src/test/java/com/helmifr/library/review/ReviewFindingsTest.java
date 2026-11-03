package com.helmifr.library.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regression tests for the findings of the independent review of the copies, holds, fines, book,
 * report and receipt features. Each test failed before its fix.
 */
@IntegrationTest
@DisplayName("Review findings (regressions)")
class ReviewFindingsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    UserAccount librarian;
    UserAccount siti;
    Book book;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        siti = data.customer("Siti Rahma");
        book = data.book("Bumi Manusia");
    }

    private String loanBody(Book b, Long memberId, LocalDate borrow, LocalDate due, LocalDate returned) {
        return """
                {"bookId": %d, "memberId": %d, "borrowDate": "%s", "dueDate": "%s"%s}"""
                .formatted(b.getId(), memberId, borrow, due, returned == null ? "" : ", \"returnDate\": \"" + returned + "\"");
    }

    // ---- Books with open requests ------------------------------------------------------------

    @Test
    @DisplayName("a book with open requests or holds cannot be deleted")
    void bookWithOpenRequestsNotDeleted() throws Exception {
        data.pendingRequest(book, siti.getMember());

        api.as(librarian).delete("/api/books/" + book.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("open requests")));
    }

    // ---- Editing a loan cannot bypass the return rules ----------------------------------------

    @Test
    @DisplayName("editing cannot return a book (no fee, queue skipped): returns go through Return")
    void editCannotReturn() throws Exception {
        Loan lent = data.activeLoan(book, siti.getMember(), TODAY.minusDays(20), TODAY.minusDays(6));

        api.as(librarian).put("/api/borrowed-books/" + lent.getId(),
                        loanBody(book, siti.getMember().getId(), TODAY.minusDays(20), TODAY.minusDays(6), TODAY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Return")));
    }

    @Test
    @DisplayName("editing cannot reopen a returned loan or move a loan to another book")
    void editCannotReopenOrSwapBook() throws Exception {
        Loan returned = data.loan(book, siti.getMember(), LoanStatus.RETURNED, TODAY.minusDays(20), TODAY.minusDays(6),
                TODAY.minusDays(8));
        Loan lent = data.activeLoan(data.book("Laskar Pelangi"), siti.getMember(), TODAY.minusDays(2), TODAY.plusDays(12));

        api.as(librarian).put("/api/borrowed-books/" + returned.getId(),
                        loanBody(book, siti.getMember().getId(), TODAY.minusDays(20), TODAY.minusDays(6), null))
                .andExpect(status().isConflict());
        api.as(librarian).put("/api/borrowed-books/" + lent.getId(),
                        loanBody(data.book("Supernova"), siti.getMember().getId(), TODAY.minusDays(2), TODAY.plusDays(12), null))
                .andExpect(status().isConflict());
    }

    // ---- Fines follow corrections ---------------------------------------------------------------

    private Loan returnLate(int daysLate) throws Exception {
        LocalDate due = TODAY.minusDays(daysLate);
        Loan loan = data.activeLoan(book, siti.getMember(), due.minusDays(14), due);
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/return").andExpect(status().isOk());
        return loan;
    }

    @Test
    @DisplayName("correcting the due date of a returned loan recalculates its unpaid fee")
    void correctionRecalculatesFee() throws Exception {
        Loan loan = returnLate(25);
        api.as(siti).get("/api/my/fines").andExpect(jsonPath("$.balance").value(25000));

        api.as(librarian).put("/api/borrowed-books/" + loan.getId(),
                        loanBody(book, siti.getMember().getId(), TODAY.minusDays(39), TODAY.minusDays(3), TODAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fine.amount").value(3000));
        api.as(siti).get("/api/my/fines")
                .andExpect(jsonPath("$.balance").value(3000))
                .andExpect(jsonPath("$.blocked").value(false));

        api.as(librarian).put("/api/borrowed-books/" + loan.getId(),
                        loanBody(book, siti.getMember().getId(), TODAY.minusDays(39), TODAY, TODAY))
                .andExpect(status().isOk());
        api.as(siti).get("/api/my/fines")
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.fines[0].status").value("WAIVED"));
        api.as(librarian).get("/api/borrowed-books/" + loan.getId() + "/events")
                .andExpect(jsonPath("$[*].type", org.hamcrest.Matchers.hasItem("FINE_ADJUSTED")));
    }

    @Test
    @DisplayName("a correction that would change a fee already paid is refused")
    void settledFeeNotChangedByCorrection() throws Exception {
        Loan loan = returnLate(4);
        long fineId = ((Number) JsonPath.read(api.as(librarian).get("/api/fines").andReturn().getResponse()
                .getContentAsString(), "$.content[0].id")).longValue();
        api.as(librarian).post("/api/fines/" + fineId + "/pay").andExpect(status().isOk());

        api.as(librarian).put("/api/borrowed-books/" + loan.getId(),
                        loanBody(book, siti.getMember().getId(), TODAY.minusDays(18), TODAY, TODAY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already")));
    }

    @Test
    @DisplayName("an unpaid fee on a deleted loan no longer counts against the member")
    void deletedLoanFeeIgnored() throws Exception {
        Loan loan = returnLate(25);

        api.as(librarian).delete("/api/borrowed-books/" + loan.getId()).andExpect(status().isNoContent());

        api.as(siti).get("/api/my/fines").andExpect(jsonPath("$.balance").value(0)).andExpect(jsonPath("$.blocked").value(false));
        api.as(librarian).get("/api/dashboard").andExpect(jsonPath("$.unpaidFines").value(0));
    }

    // ---- Receipts ----------------------------------------------------------------------------------

    private String issue(Loan loan) throws Exception {
        return api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/receipt").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("a receipt stays with the member it was issued to, even if the loan's member is corrected")
    void receiptFollowsSnapshotMember() throws Exception {
        Loan loan = data.activeLoan(book, siti.getMember(), TODAY.minusDays(2), TODAY.plusDays(12));
        String number = JsonPath.read(issue(loan), "$.number");
        UserAccount bayu = data.customer("Bayu");

        api.as(librarian).put("/api/borrowed-books/" + loan.getId(),
                        loanBody(book, bayu.getMember().getId(), TODAY.minusDays(2), TODAY.plusDays(12), null))
                .andExpect(status().isOk());

        api.as(siti).get("/api/receipts/" + number).andExpect(status().isOk());
        api.as(bayu).get("/api/receipts/" + number).andExpect(status().isNotFound());
        api.as(bayu).get("/api/receipts/" + number + ".pdf").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a signed record moved onto another receipt's row does not verify")
    void contentBoundToItsRow() throws Exception {
        Loan first = data.activeLoan(book, siti.getMember(), TODAY.minusDays(2), TODAY.plusDays(12));
        Loan second = data.activeLoan(data.book("Supernova"), siti.getMember(), TODAY.minusDays(1), TODAY.plusDays(13));
        String a = issue(first);
        String b = issue(second);
        em.flush();
        var source = jdbc.queryForMap("SELECT content, sha256, signature FROM receipts WHERE number = ?",
                (String) JsonPath.read(a, "$.number"));
        jdbc.update("UPDATE receipts SET content = ?, sha256 = ?, signature = ? WHERE number = ?",
                source.get("content"), source.get("sha256"), source.get("signature"), (String) JsonPath.read(b, "$.number"));
        em.clear();
        String code = JsonPath.read(api.as(librarian).get("/api/receipts/" + JsonPath.read(b, "$.number"))
                .andReturn().getResponse().getContentAsString(), "$.verifyCode");

        api.anonymous().get("/api/receipts/verify/" + JsonPath.read(b, "$.number"), "code", code)
                .andExpect(jsonPath("$.genuine").value(false));
    }

    @Test
    @DisplayName("checking an older receipt points to the newer one for the same loan")
    void supersededReceipt() throws Exception {
        Loan loan = data.activeLoan(book, siti.getMember(), TODAY.minusDays(2), TODAY.plusDays(12));
        String old = issue(loan);
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/return").andExpect(status().isOk());
        String newer = issue(loan);

        api.anonymous().get("/api/receipts/verify/" + JsonPath.read(old, "$.number"), "code", (String) JsonPath.read(old, "$.verifyCode"))
                .andExpect(jsonPath("$.genuine").value(true))
                .andExpect(jsonPath("$.supersededBy").value((String) JsonPath.read(newer, "$.number")));
        api.anonymous().get("/api/receipts/verify/" + JsonPath.read(newer, "$.number"), "code", (String) JsonPath.read(newer, "$.verifyCode"))
                .andExpect(jsonPath("$.supersededBy").doesNotExist());
    }

    @Test
    @DisplayName("a loan with a very long history still gets a receipt: the oldest entries are summarised")
    void longHistory() throws Exception {
        Loan loan = data.activeLoan(book, siti.getMember(), TODAY.minusDays(2), TODAY.plusDays(12));
        for (int i = 0; i < 120; i++) {
            api.as(librarian).put("/api/borrowed-books/" + loan.getId(),
                            loanBody(book, siti.getMember().getId(), TODAY.minusDays(2), TODAY.plusDays(12 + i % 3), null))
                    .andExpect(status().isOk());
        }

        String body = issue(loan);

        assertThat((Integer) JsonPath.read(body, "$.content.events.length()")).isLessThanOrEqualTo(41);
        assertThat((String) JsonPath.read(body, "$.content.events[0].type")).isEqualTo("OMITTED");
        assertThat((String) JsonPath.read(body, "$.content.events[0].detail")).contains("80 earlier entries");
    }

    @Test
    @DisplayName("receipts name staff and members by their display name, not their login")
    void displayNamesNotLogins() throws Exception {
        Loan loan = data.activeLoan(book, siti.getMember(), TODAY.minusDays(2), TODAY.plusDays(12));
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/renew").andExpect(status().isOk());

        String body = issue(loan);

        assertThat((String) JsonPath.read(body, "$.content.events[0].actor")).isEqualTo(librarian.getDisplayName());
        assertThat((String) JsonPath.read(body, "$.content.issuedBy")).isEqualTo(librarian.getDisplayName());
        assertThat(body).doesNotContain("\"" + librarian.getUsername() + "\"");
    }

    // ---- CSV in semicolon locales -------------------------------------------------------------

    @Test
    @DisplayName("text cells in CSV exports are always quoted, so a ';' in a name cannot start a formula cell")
    void csvQuotesText() throws Exception {
        var member = data.member("Budi;=cmd|' /C calc'!A0");
        data.activeLoan(book, member, TODAY.minusDays(1), TODAY.plusDays(13));

        String csv = api.as(librarian).get("/api/reports/loans.csv").andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).contains(",\"Budi;=cmd|' /C calc'!A0\",").contains(",\"Bumi Manusia\",");
    }
}
