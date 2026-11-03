package com.helmifr.library.member;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.account.UserAccountRepository;
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
 * Merging duplicate member records, e.g. a walk-in card and a record created when the same person
 * registered online without it: everything the duplicate had moves to the record kept.
 */
@IntegrationTest
@DisplayName("Merging duplicate members")
class MemberMergeTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired MemberRepository members;
    @Autowired UserAccountRepository accounts;
    UserAccount librarian;
    Member walkIn;
    UserAccount online;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        walkIn = data.member("Siti Rahayu");
        online = data.customer("Siti R");
    }

    private org.springframework.test.web.servlet.ResultActions merge(Member keep, Member duplicate) throws Exception {
        return api.as(librarian).post("/api/members/" + keep.getId() + "/merge",
                "{\"duplicateId\": %d}".formatted(duplicate.getId()));
    }

    /** The account as a session signed in now holds it (it does not follow later changes). */
    private static UserAccount snapshot(UserAccount account) {
        UserAccount copy = new UserAccount();
        copy.setId(account.getId());
        copy.setUsername(account.getUsername());
        copy.setPasswordHash(account.getPasswordHash());
        copy.setRole(account.getRole());
        copy.setEnabled(account.isEnabled());
        copy.setMember(account.getMember());
        return copy;
    }

    @Test
    @DisplayName("loans, fines, receipts and the online account move to the member kept; the duplicate is removed")
    void mergeMovesEverything() throws Exception {
        Member duplicate = online.getMember();
        Book lateBook = data.book("Bumi Manusia");
        Loan late = data.activeLoan(lateBook, duplicate, TODAY.minusDays(20), TODAY.minusDays(4));
        api.as(librarian).post("/api/borrowed-books/" + late.getId() + "/return").andExpect(status().isOk());
        String receipt = api.as(librarian).post("/api/borrowed-books/" + late.getId() + "/receipt")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        data.activeLoan(data.book("Laskar Pelangi"), duplicate, TODAY.minusDays(2), TODAY.plusDays(12));
        data.activeLoan(data.book("Supernova"), walkIn, TODAY.minusDays(1), TODAY.plusDays(13));

        UserAccount sessionBeforeMerge = snapshot(online);

        merge(walkIn, duplicate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(walkIn.getId()))
                .andExpect(jsonPath("$.libraryCardId").value(walkIn.getLibraryCardId()))
                .andExpect(jsonPath("$.accountUsername").value(online.getUsername()));

        api.as(librarian).get("/api/members/" + walkIn.getId() + "/loans").andExpect(jsonPath("$.length()").value(3));
        api.as(librarian).get("/api/members/" + duplicate.getId()).andExpect(status().isNotFound());
        api.as(librarian).get("/api/fines", "q", walkIn.getLibraryCardId())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(4000));
        // A session that started before the merge still points at the old record: it is ended.
        api.as(sessionBeforeMerge).get("/api/my/loans").andExpect(status().isUnauthorized());
        UserAccount signedInAgain = accounts.findById(online.getId()).orElseThrow();
        api.as(signedInAgain).get("/api/my/loans").andExpect(jsonPath("$.length()").value(3));
        api.as(signedInAgain).get("/api/my/fines").andExpect(jsonPath("$.balance").value(4000));
        api.as(signedInAgain).get("/api/receipts/" + JsonPath.read(receipt, "$.number")).andExpect(status().isOk());
        api.as(librarian).get("/api/members/" + walkIn.getId() + "/cards")
                .andExpect(jsonPath("$[0].libraryCardId").value(duplicate.getLibraryCardId()))
                .andExpect(jsonPath("$[0].reason").value("MERGED"));
        api.as(librarian).get("/api/members", "q", duplicate.getLibraryCardId())
                .andExpect(jsonPath("$.content[0].id").value(walkIn.getId()));
    }

    @Test
    @DisplayName("the later of the two expiry dates is kept")
    void laterExpiryKept() throws Exception {
        Member keep = members.findById(walkIn.getId()).orElseThrow();
        keep.setCardExpiresOn(TODAY.minusDays(3));
        members.saveAndFlush(keep);
        Member duplicate = members.findById(online.getMember().getId()).orElseThrow();
        duplicate.setCardExpiresOn(TODAY.plusMonths(6));
        members.saveAndFlush(duplicate);

        merge(walkIn, duplicate).andExpect(jsonPath("$.cardExpiresOn").value(TODAY.plusMonths(6).toString()));
    }

    @Test
    @DisplayName("the card history of the duplicate moves too")
    void historyMoves() throws Exception {
        Member duplicate = online.getMember();
        String lost = duplicate.getLibraryCardId();
        api.as(librarian).post("/api/members/" + duplicate.getId() + "/card/replace", "{\"reason\": \"Lost\"}")
                .andExpect(status().isOk());

        merge(walkIn, duplicate).andExpect(status().isOk());

        api.as(librarian).get("/api/members/" + walkIn.getId() + "/cards").andExpect(jsonPath("$.length()").value(2));
        api.as(librarian).get("/api/members", "q", lost).andExpect(jsonPath("$.content[0].id").value(walkIn.getId()));
    }

    @Test
    @DisplayName("a member cannot be merged with itself, nor two members that both have online accounts")
    void refusals() throws Exception {
        UserAccount other = data.customer("Siti Lain");

        merge(walkIn, walkIn).andExpect(status().isConflict());
        merge(online.getMember(), other.getMember())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("online account")));
    }

    @Test
    @DisplayName("two members with an open request or loan for the same book are not merged")
    void sameBookOpen() throws Exception {
        Book book = data.bookWithCopies("Cantik Itu Luka", 2);
        data.activeLoan(book, walkIn, TODAY.minusDays(1), TODAY.plusDays(13));
        data.pendingRequest(book, online.getMember());

        merge(walkIn, online.getMember())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Cantik Itu Luka")));
    }

    @Test
    @DisplayName("a waiting request keeps its place in the queue after the merge")
    void queuePlaceKept() throws Exception {
        Book book = data.bookWithCopies("Ronggeng Dukuh Paruk", 1);
        data.activeLoan(book, data.member("Bayu"), TODAY.minusDays(1), TODAY.plusDays(13));
        Loan first = data.loan(book, online.getMember(), LoanStatus.WAITLISTED, null, null, null);
        data.loan(book, data.member("Ani"), LoanStatus.WAITLISTED, null, null, null);

        merge(walkIn, online.getMember()).andExpect(status().isOk());

        api.as(librarian).get("/api/borrowed-books/" + first.getId())
                .andExpect(jsonPath("$.member.id").value(walkIn.getId()))
                .andExpect(jsonPath("$.queuePosition").value(1));
    }

    @Test
    @DisplayName("a deleted member cannot be merged, and only librarians merge")
    void deletedAndForbidden() throws Exception {
        Member gone = data.member("Gone");
        api.as(librarian).delete("/api/members/" + gone.getId()).andExpect(status().isNoContent());

        merge(walkIn, gone).andExpect(status().isNotFound());
        api.as(online).post("/api/members/" + walkIn.getId() + "/merge",
                "{\"duplicateId\": %d}".formatted(online.getMember().getId())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the online account of the member kept stays when the duplicate has none")
    void keepsOwnAccount() throws Exception {
        merge(online.getMember(), walkIn)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountUsername").value(online.getUsername()));
        org.assertj.core.api.Assertions.assertThat(accounts.findByMemberId(online.getMember().getId())).isPresent();
    }
}
