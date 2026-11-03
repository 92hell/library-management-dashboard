package com.helmifr.library.member;

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

/**
 * Library cards: a card is valid for 12 months and is renewed at the desk; an expired card cannot
 * borrow. A lost card is replaced with a new ID; the old ID is kept in the member's card history.
 */
@IntegrationTest
@DisplayName("Library cards: expiry, renewal and replacement")
class MemberCardsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired MemberRepository members;
    UserAccount librarian;
    UserAccount siti;
    Book book;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        siti = data.customer("Siti");
        book = data.book("Bumi Manusia");
    }

    private void cardExpires(Member member, LocalDate on) {
        Member m = members.findById(member.getId()).orElseThrow();
        m.setCardExpiresOn(on);
        members.saveAndFlush(m);
    }

    private static final String NEW_MEMBER = """
            {"name": "Rina Wijaya", "dateOfBirth": "1995-03-04", "email": "rina@example.com", "phone": "0812345678"}""";

    @Test
    @DisplayName("a new card is valid for 12 months")
    void newCardValidForAYear() throws Exception {
        api.as(librarian).post("/api/members", NEW_MEMBER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardExpiresOn").value(TODAY.plusMonths(12).toString()))
                .andExpect(jsonPath("$.cardExpired").value(false));
    }

    @Test
    @DisplayName("a card that expires today still works today")
    void lastDayStillValid() throws Exception {
        cardExpires(siti.getMember(), TODAY);

        api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(book.getId())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("with an expired card a member cannot request, borrow at the desk, collect a hold or renew a loan")
    void expiredCardCannotBorrow() throws Exception {
        Book lent = data.book("Laskar Pelangi");
        Loan active = data.activeLoan(lent, siti.getMember(), TODAY.minusDays(3), TODAY.plusDays(11));
        Loan pending = data.pendingRequest(data.book("Supernova"), siti.getMember());
        Book held = data.book("Ronggeng Dukuh Paruk");
        Loan ready = data.loan(held, siti.getMember(), LoanStatus.READY, null, null, null);
        ready.setCopy(data.copy(held));
        cardExpires(siti.getMember(), TODAY.minusDays(1));

        api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(book.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("expired on " + TODAY.minusDays(1))));
        api.as(librarian).post("/api/borrowed-books",
                        "{\"bookId\": %d, \"memberId\": %d}".formatted(book.getId(), siti.getMember().getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.memberId", containsString("expired")));
        api.as(librarian).post("/api/borrowed-books/" + pending.getId() + "/approve", "{}")
                .andExpect(status().isConflict());
        api.as(librarian).post("/api/borrowed-books/" + ready.getId() + "/checkout")
                .andExpect(status().isConflict());
        api.as(siti).get("/api/my/loans")
                .andExpect(jsonPath("$[?(@.id == %d)].canRenew".formatted(active.getId())).value(false));
        api.as(siti).post("/api/my/loans/" + active.getId() + "/renew")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("card has expired")));
    }

    @Test
    @DisplayName("a past loan can still be recorded for a member whose card has since expired")
    void pastLoanForExpiredCard() throws Exception {
        cardExpires(siti.getMember(), TODAY.minusDays(1));

        api.as(librarian).post("/api/borrowed-books", """
                        {"bookId": %d, "memberId": %d, "borrowDate": "%s", "dueDate": "%s", "returnDate": "%s"}"""
                        .formatted(book.getId(), siti.getMember().getId(), TODAY.minusDays(40), TODAY.minusDays(26),
                                TODAY.minusDays(30)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("renewing an expired card gives 12 months from today")
    void renewExpired() throws Exception {
        cardExpires(siti.getMember(), TODAY.minusDays(10));

        api.as(librarian).post("/api/members/" + siti.getMember().getId() + "/card/renew")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardExpiresOn").value(TODAY.plusMonths(12).toString()))
                .andExpect(jsonPath("$.cardExpired").value(false));
        api.as(siti).post("/api/my/loans", "{\"bookId\": %d}".formatted(book.getId())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("renewing early adds 12 months to the current expiry, so no days are lost")
    void renewEarly() throws Exception {
        cardExpires(siti.getMember(), TODAY.plusDays(20));

        api.as(librarian).post("/api/members/" + siti.getMember().getId() + "/card/renew")
                .andExpect(jsonPath("$.cardExpiresOn").value(TODAY.plusDays(20).plusMonths(12).toString()));
    }

    @Test
    @DisplayName("holds skip a member whose card has expired, and serve them once it is renewed")
    void holdsWaitForRenewal() throws Exception {
        Book one = data.bookWithCopies("Cantik Itu Luka", 1);
        Loan out = data.activeLoan(one, data.member("Bayu"), TODAY.minusDays(5), TODAY.plusDays(9));
        Loan hold = data.loan(one, siti.getMember(), LoanStatus.WAITLISTED, null, null, null);
        cardExpires(siti.getMember(), TODAY.minusDays(1));

        api.as(librarian).post("/api/borrowed-books/" + out.getId() + "/return").andExpect(status().isOk());
        api.as(librarian).get("/api/borrowed-books/" + hold.getId()).andExpect(jsonPath("$.status").value("WAITLISTED"));

        api.as(librarian).post("/api/members/" + siti.getMember().getId() + "/card/renew").andExpect(status().isOk());

        api.as(librarian).get("/api/borrowed-books/" + hold.getId()).andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    @DisplayName("members see their card's expiry on their profile")
    void profileShowsExpiry() throws Exception {
        cardExpires(siti.getMember(), TODAY.plusDays(20));

        api.as(siti).get("/api/profile")
                .andExpect(jsonPath("$.member.cardExpiresOn").value(TODAY.plusDays(20).toString()))
                .andExpect(jsonPath("$.member.cardExpired").value(false));
    }

    @Test
    @DisplayName("a replaced card gets a new ID; the old one is kept in the history and still finds the member")
    void replaceLostCard() throws Exception {
        Member member = siti.getMember();
        String oldId = member.getLibraryCardId();
        data.activeLoan(book, member, TODAY.minusDays(3), TODAY.plusDays(11));

        String body = api.as(librarian).post("/api/members/" + member.getId() + "/card/replace", "{\"reason\": \"Lost\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libraryCardId").value(org.hamcrest.Matchers.startsWith("LIB-")))
                .andReturn().getResponse().getContentAsString();
        String newId = JsonPath.read(body, "$.libraryCardId");
        org.assertj.core.api.Assertions.assertThat(newId).isNotEqualTo(oldId);

        api.as(librarian).get("/api/members/" + member.getId() + "/cards")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].libraryCardId").value(oldId))
                .andExpect(jsonPath("$[0].reason").value("REPLACED"))
                .andExpect(jsonPath("$[0].note").value("Lost"))
                .andExpect(jsonPath("$[0].retiredBy").value(librarian.getUsername()))
                .andExpect(jsonPath("$[0].retiredAt").isNotEmpty());
        api.as(librarian).get("/api/members", "q", oldId)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].libraryCardId").value(newId));
        api.as(librarian).get("/api/members/" + member.getId() + "/loans").andExpect(jsonPath("$.length()").value(1));
        api.as(siti).get("/api/profile").andExpect(jsonPath("$.member.libraryCardId").value(newId));
    }

    @Autowired LibraryCardIds libraryCardIds;

    @Test
    @DisplayName("a replaced card ID is never issued to anyone again")
    void oldIdsNeverReissued() throws Exception {
        String oldId = siti.getMember().getLibraryCardId();
        org.assertj.core.api.Assertions.assertThat(libraryCardIds.taken("LIB-NOBODY")).isFalse();

        api.as(librarian).post("/api/members/" + siti.getMember().getId() + "/card/replace", "{\"reason\": \"Lost\"}")
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(libraryCardIds.taken(oldId)).isTrue();
        org.assertj.core.api.Assertions.assertThat(libraryCardIds.taken(oldId.toLowerCase())).isTrue();
    }

    @Test
    @DisplayName("an old card ID can no longer be used to register")
    void oldCardCannotRegister() throws Exception {
        Member walkIn = data.member("Rina");
        String oldId = walkIn.getLibraryCardId();
        api.as(librarian).post("/api/members/" + walkIn.getId() + "/card/replace", "{\"reason\": \"Damaged\"}")
                .andExpect(status().isOk());

        api.anonymous().post("/api/auth/register", """
                        {"username": "rina", "password": "a-long-password", "name": "Rina", "email": "%s",
                         "phone": "0899999999", "dateOfBirth": "1995-03-04", "libraryCardId": "%s"}"""
                        .formatted(walkIn.getEmail(), oldId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("replacing a card needs a reason")
    void replaceNeedsReason() throws Exception {
        api.as(librarian).post("/api/members/" + siti.getMember().getId() + "/card/replace", "{\"reason\": \" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
    }

    @Test
    @DisplayName("a receipt issued before the card was replaced still verifies")
    void receiptsSurviveReplacement() throws Exception {
        Loan loan = data.activeLoan(book, siti.getMember(), TODAY.minusDays(3), TODAY.plusDays(11));
        String body = api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/receipt")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        api.as(librarian).post("/api/members/" + siti.getMember().getId() + "/card/replace", "{\"reason\": \"Lost\"}")
                .andExpect(status().isOk());

        api.anonymous().get("/api/receipts/verify/" + JsonPath.read(body, "$.number"), "code",
                        (String) JsonPath.read(body, "$.verifyCode"))
                .andExpect(jsonPath("$.genuine").value(true));
    }

    @Test
    @DisplayName("only librarians renew or replace cards")
    void librariansOnly() throws Exception {
        Long id = siti.getMember().getId();

        api.as(siti).post("/api/members/" + id + "/card/renew").andExpect(status().isForbidden());
        api.as(siti).post("/api/members/" + id + "/card/replace", "{\"reason\": \"Lost\"}").andExpect(status().isForbidden());
        api.as(siti).get("/api/members/" + id + "/cards").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the card validity can be set to 0 months: cards then never expire")
    void neverExpire() {
        MemberCardPolicy never = new MemberCardPolicy(0);
        MemberCardPolicy yearly = new MemberCardPolicy(12);

        org.assertj.core.api.Assertions.assertThat(never.expiryFrom(TODAY)).isNull();
        org.assertj.core.api.Assertions.assertThat(yearly.expiryFrom(TODAY)).isEqualTo(TODAY.plusMonths(12));
        org.assertj.core.api.Assertions.assertThat(yearly.renewed(TODAY.minusDays(1), TODAY)).isEqualTo(TODAY.plusMonths(12));
        org.assertj.core.api.Assertions.assertThat(yearly.renewed(TODAY.plusDays(5), TODAY))
                .isEqualTo(TODAY.plusDays(5).plusMonths(12));
        org.assertj.core.api.Assertions.assertThat(yearly.renewed(null, TODAY)).isEqualTo(TODAY.plusMonths(12));
    }
}
