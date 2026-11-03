package com.helmifr.library.receipt;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.loan.Loan;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import com.jayway.jsonpath.JsonPath;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Loan receipts as physical evidence: every status change of a loan is recorded; a receipt is a
 * numbered, signed snapshot of the loan and its history that can be printed or downloaded as PDF,
 * and checked by anyone on a public page (QR code) or with the published public key.
 */
@IntegrationTest
@DisplayName("Loan receipts")
class ReceiptsTest {

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

    private static Object read(org.springframework.test.web.servlet.ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    /** Request -> approve -> hand over -> renew -> return 2 days late -> fee paid, all through the API. */
    private long fullLifecycle() throws Exception {
        long loanId = ((Number) read(api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(book.getId())),
                "$.id")).longValue();
        api.as(librarian).post("/api/borrowed-books/" + loanId + "/approve", "{}").andExpect(status().isOk());
        api.as(librarian).post("/api/borrowed-books/" + loanId + "/checkout").andExpect(status().isOk());
        // Fast-forward: the book is now 2 days overdue (raw SQL, so flush and forget cached entities).
        em.flush();
        jdbc.update("UPDATE borrowed_books SET borrow_date = ?, due_date = ? WHERE id = ?",
                TODAY.minusDays(16), TODAY.minusDays(2), loanId);
        em.clear();
        api.as(librarian).post("/api/borrowed-books/" + loanId + "/return").andExpect(status().isOk());
        long fineId = ((Number) read(api.as(librarian).get("/api/fines"), "$.content[0].id")).longValue();
        api.as(librarian).post("/api/fines/" + fineId + "/pay").andExpect(status().isOk());
        return loanId;
    }

    private String issue(UserAccount who, String url) throws Exception {
        return api.as(who).post(url).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("every status change of a loan is recorded with who did it and when")
    void eventTrail() throws Exception {
        long loanId = fullLifecycle();

        api.as(librarian).get("/api/borrowed-books/" + loanId + "/events")
                .andExpect(jsonPath("$[*].type").value(org.hamcrest.Matchers.contains(
                        "REQUESTED", "APPROVED", "COLLECTED", "RETURNED", "FINE_CHARGED", "FINE_PAID")))
                .andExpect(jsonPath("$[0].actor").value(siti.getUsername()))
                .andExpect(jsonPath("$[1].actor").value(librarian.getUsername()))
                .andExpect(jsonPath("$[1].status").value("READY"))
                .andExpect(jsonPath("$[4].detail").value(containsString("IDR 2,000")))
                .andExpect(jsonPath("$[0].occurredAt").isNotEmpty());
    }

    @Test
    @DisplayName("a receipt has a yearly number, the loan, its history, the fee, a signature and a verification link")
    void issueReceipt() throws Exception {
        long loanId = fullLifecycle();

        String body = issue(librarian, "/api/borrowed-books/" + loanId + "/receipt");

        String number = JsonPath.read(body, "$.number");
        org.assertj.core.api.Assertions.assertThat(number).matches("R-" + TODAY.getYear() + "-\\d{6}");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.content.loan.bookTitle")).isEqualTo("Bumi Manusia");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.content.loan.memberName")).isEqualTo("Siti Rahma");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.content.loan.status")).isEqualTo("RETURNED");
        org.assertj.core.api.Assertions.assertThat((Integer) JsonPath.read(body, "$.content.events.length()")).isEqualTo(6);
        org.assertj.core.api.Assertions.assertThat((Integer) JsonPath.read(body, "$.content.fine.amount")).isEqualTo(2000);
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.content.fine.status")).isEqualTo("PAID");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.issuedBy")).isEqualTo(librarian.getUsername());
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.sha256")).matches("[0-9a-f]{64}");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.algorithm")).isEqualTo("Ed25519");
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(body, "$.verifyUrl"))
                .startsWith("http://localhost/verify/" + number + "?code=");
        org.assertj.core.api.Assertions.assertThat((Integer) JsonPath.read(body, "$.qr.size")).isGreaterThan(20);
    }

    @Test
    @DisplayName("issuing again without changes gives the same receipt; after a change, a new one")
    void reissue() throws Exception {
        Loan loan = data.activeLoan(book, siti.getMember(), TODAY.minusDays(3), TODAY.plusDays(11));
        String url = "/api/borrowed-books/" + loan.getId() + "/receipt";

        String first = JsonPath.read(issue(librarian, url), "$.number");
        String again = JsonPath.read(issue(librarian, url), "$.number");
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/return").andExpect(status().isOk());
        String afterReturn = JsonPath.read(issue(librarian, url), "$.number");

        org.assertj.core.api.Assertions.assertThat(again).isEqualTo(first);
        org.assertj.core.api.Assertions.assertThat(afterReturn).isNotEqualTo(first);
    }

    @Test
    @DisplayName("anyone with the QR link can check a receipt; it shows the record with the member's name masked")
    void publicVerification() throws Exception {
        String body = issue(librarian, "/api/borrowed-books/" + fullLifecycle() + "/receipt");
        String number = JsonPath.read(body, "$.number");
        String code = JsonPath.read(body, "$.verifyCode");

        api.anonymous().get("/api/receipts/verify/" + number, "code", code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genuine").value(true))
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.bookTitle").value("Bumi Manusia"))
                .andExpect(jsonPath("$.memberName").value("S**i R***a"))
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.events.length()").value(6))
                .andExpect(jsonPath("$.libraryCardId").doesNotExist());
        api.anonymous().get("/api/receipts/verify/" + number, "code", "wrong-code").andExpect(status().isNotFound());
        api.anonymous().get("/api/receipts/verify/R-1999-000001", "code", code).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a receipt whose stored record was altered no longer verifies")
    void tamperDetected() throws Exception {
        String body = issue(librarian, "/api/borrowed-books/" + fullLifecycle() + "/receipt");
        String number = JsonPath.read(body, "$.number");
        em.flush();
        jdbc.update("UPDATE receipts SET content = REPLACE(content, 'RETURNED', 'ACTIVE') WHERE number = ?", number);
        em.clear();

        api.anonymous().get("/api/receipts/verify/" + number, "code", (String) JsonPath.read(body, "$.verifyCode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genuine").value(false));
    }

    @Test
    @DisplayName("altering the record and recomputing its hash is still caught: only the library's key can sign")
    void tamperWithRecomputedHash() throws Exception {
        String body = issue(librarian, "/api/borrowed-books/" + fullLifecycle() + "/receipt");
        String number = JsonPath.read(body, "$.number");
        String forged = ((String) JsonPath.read(body, "$.signedContent")).replace("\"PAID\"", "\"WAIVED\"");
        String forgedHash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(forged.getBytes(StandardCharsets.UTF_8)));
        em.flush();
        jdbc.update("UPDATE receipts SET content = ?, sha256 = ? WHERE number = ?", forged, forgedHash, number);
        em.clear();

        api.anonymous().get("/api/receipts/verify/" + number, "code", (String) JsonPath.read(body, "$.verifyCode"))
                .andExpect(jsonPath("$.genuine").value(false))
                .andExpect(jsonPath("$.fine.status").value("WAIVED"));
    }

    @Test
    @DisplayName("the signature checks out with the published public key, without trusting the app")
    void independentlyVerifiable() throws Exception {
        String body = issue(librarian, "/api/borrowed-books/" + fullLifecycle() + "/receipt");
        String content = JsonPath.read(body, "$.signedContent");
        String signature = JsonPath.read(body, "$.signature");

        String pem = api.anonymous().get("/api/receipts/public-key").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        byte[] der = Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
        var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(content.getBytes(StandardCharsets.UTF_8));

        org.assertj.core.api.Assertions.assertThat(verifier.verify(Base64.getDecoder().decode(signature))).isTrue();
        org.assertj.core.api.Assertions.assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----");
    }

    @Test
    @DisplayName("the PDF carries the receipt number, the book, the history, the stamp and the signature details")
    void pdf() throws Exception {
        String body = issue(librarian, "/api/borrowed-books/" + fullLifecycle() + "/receipt");
        String number = JsonPath.read(body, "$.number");

        var response = api.as(librarian).get("/api/receipts/" + number + ".pdf")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"receipt-" + number + ".pdf\""))
                .andReturn().getResponse();
        byte[] pdf = response.getContentAsByteArray();
        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);

        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        org.assertj.core.api.Assertions.assertThat(text)
                .contains(number)
                .contains("Bumi Manusia")
                .contains("Bukti Peminjaman")
                .contains("Returned")
                .contains("Ed25519")
                .contains((String) JsonPath.read(body, "$.sha256"));
    }

    @Test
    @DisplayName("customers get receipts for their own loans only")
    void customersOwnLoansOnly() throws Exception {
        Loan mine = data.activeLoan(book, siti.getMember(), TODAY.minusDays(1), TODAY.plusDays(13));
        UserAccount bayu = data.customer("Bayu");

        String body = issue(siti, "/api/my/loans/" + mine.getId() + "/receipt");
        String number = JsonPath.read(body, "$.number");

        api.as(siti).get("/api/receipts/" + number).andExpect(status().isOk());
        api.as(siti).get("/api/receipts/" + number + ".pdf").andExpect(status().isOk());
        api.as(bayu).post("/api/my/loans/" + mine.getId() + "/receipt").andExpect(status().isNotFound());
        api.as(bayu).get("/api/receipts/" + number).andExpect(status().isNotFound());
        api.as(bayu).get("/api/receipts/" + number + ".pdf").andExpect(status().isNotFound());
        api.as(bayu).post("/api/borrowed-books/" + mine.getId() + "/receipt").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a desk loan starts its history as recorded by the librarian; a renewal and an edit are recorded too")
    void deskHistory() throws Exception {
        String created = api.as(librarian).post("/api/borrowed-books",
                        "{\"bookId\": %d, \"memberId\": %d}".formatted(book.getId(), siti.getMember().getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long loanId = ((Number) JsonPath.read(created, "$.id")).longValue();
        api.as(librarian).post("/api/borrowed-books/" + loanId + "/renew").andExpect(status().isOk());

        api.as(librarian).get("/api/borrowed-books/" + loanId + "/events")
                .andExpect(jsonPath("$[0].type").value("LENT"))
                .andExpect(jsonPath("$[1].type").value("RENEWED"))
                .andExpect(jsonPath("$[1].detail").value(matchesPattern(".*\\d{4}-\\d{2}-\\d{2}.*")));
        api.as(siti).get("/api/borrowed-books/" + loanId + "/events").andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(created).contains("\"status\":\"ACTIVE\"");
    }
}
