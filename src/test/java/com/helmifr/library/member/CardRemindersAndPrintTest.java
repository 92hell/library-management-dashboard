package com.helmifr.library.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.zxing.common.BitArray;
import com.google.zxing.oned.Code128Reader;
import com.helmifr.library.account.UserAccount;
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
 * A month before a library card expires the member is reminded (in the app, and by email or SMS
 * where those are set up), once per expiry date. Every card can be printed, with a barcode the
 * desk scanner reads.
 */
@IntegrationTest
@DisplayName("Library cards: expiry reminders and the printable card")
class CardRemindersAndPrintTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired MemberRepository members;
    @Autowired MemberCardReminderJob reminders;
    UserAccount librarian;
    UserAccount siti;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        siti = data.customer("Siti");
    }

    private void cardExpires(Member member, LocalDate on) {
        Member m = members.findById(member.getId()).orElseThrow();
        m.setCardExpiresOn(on);
        members.saveAndFlush(m);
    }

    private org.springframework.test.web.servlet.ResultActions remindersOf(UserAccount account) throws Exception {
        return api.as(account).get("/api/notifications");
    }

    @Test
    @DisplayName("a member is reminded 30 days before the card expires, once, with a link to their profile")
    void remindedOnce() throws Exception {
        cardExpires(siti.getMember(), TODAY.plusDays(30));

        reminders.remind(TODAY);
        reminders.remind(TODAY);
        reminders.remind(TODAY.plusDays(1));

        remindersOf(siti)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("CARD_EXPIRING"))
                .andExpect(jsonPath("$.content[0].link").value("/profile"))
                .andExpect(jsonPath("$.content[0].message", containsString(siti.getMember().getLibraryCardId())))
                .andExpect(jsonPath("$.content[0].message", containsString(TODAY.plusDays(30).toString())));
    }

    @Test
    @DisplayName("no reminder when the card expires later than 30 days from now, has already expired or never expires")
    void notYetOrNever() throws Exception {
        UserAccount later = data.customer("Bayu");
        UserAccount expired = data.customer("Ani");
        UserAccount never = data.customer("Dimas");
        cardExpires(later.getMember(), TODAY.plusDays(31));
        cardExpires(expired.getMember(), TODAY.minusDays(1));
        cardExpires(never.getMember(), null);

        reminders.remind(TODAY);

        for (UserAccount account : new UserAccount[] {later, expired, never}) {
            remindersOf(account).andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Test
    @DisplayName("after a renewal the member is reminded again before the new expiry date")
    void remindedAgainAfterRenewal() throws Exception {
        cardExpires(siti.getMember(), TODAY.plusDays(10));
        reminders.remind(TODAY);
        api.as(librarian).post("/api/members/" + siti.getMember().getId() + "/card/renew").andExpect(status().isOk());

        reminders.remind(TODAY);
        remindersOf(siti).andExpect(jsonPath("$.totalElements").value(1));

        // A year on, the renewed card nears its end.
        LocalDate renewedExpiry = members.findById(siti.getMember().getId()).orElseThrow().getCardExpiresOn();
        reminders.remind(renewedExpiry.minusDays(20));
        remindersOf(siti).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("deleted and deactivated members are not reminded")
    void deletedOrInactive() throws Exception {
        Member inactive = members.findById(siti.getMember().getId()).orElseThrow();
        inactive.setActive(false);
        inactive.setCardExpiresOn(TODAY.plusDays(5));
        members.saveAndFlush(inactive);
        UserAccount gone = data.customer("Gone");
        cardExpires(gone.getMember(), TODAY.plusDays(5));
        Member deleted = members.findById(gone.getMember().getId()).orElseThrow();
        deleted.setDeleted(true);
        members.saveAndFlush(deleted);

        assertThat(reminders.remind(TODAY)).isZero();
    }

    @Test
    @DisplayName("the printable card has the library name, the member, the expiry date (and whether it has passed) and a barcode of the card ID")
    void printableCard() throws Exception {
        Member member = siti.getMember();
        cardExpires(member, TODAY.plusMonths(6));

        String json = api.as(librarian).get("/api/members/" + member.getId() + "/card")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libraryName").value("Community Library"))
                .andExpect(jsonPath("$.name").value(member.getName()))
                .andExpect(jsonPath("$.libraryCardId").value(member.getLibraryCardId()))
                .andExpect(jsonPath("$.cardExpiresOn").value(TODAY.plusMonths(6).toString()))
                .andExpect(jsonPath("$.cardExpired").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(readBarcode(JsonPath.read(json, "$.barcode"))).isEqualTo(member.getLibraryCardId());
        cardExpires(member, TODAY.minusDays(1));
        api.as(librarian).get("/api/members/" + member.getId() + "/card").andExpect(jsonPath("$.cardExpired").value(true));
    }

    @Test
    @DisplayName("a member prints their own card from their profile; librarians, who have no card, get none")
    void ownCard() throws Exception {
        api.as(siti).get("/api/profile/card")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libraryCardId").value(siti.getMember().getLibraryCardId()));
        api.as(librarian).get("/api/profile/card").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("only librarians print other members' cards, and not those of deleted members")
    void othersCards() throws Exception {
        UserAccount bayu = data.customer("Bayu");
        Member gone = data.member("Gone");
        api.as(librarian).delete("/api/members/" + gone.getId()).andExpect(status().isNoContent());

        api.as(siti).get("/api/members/" + bayu.getMember().getId() + "/card").andExpect(status().isForbidden());
        api.as(librarian).get("/api/members/" + gone.getId() + "/card").andExpect(status().isNotFound());
    }

    /** Reads a Code 128 barcode given as bars ("1") and spaces ("0"), as a scanner would. */
    private static String readBarcode(String modules) throws Exception {
        assertThat(modules).matches("[01]+");
        int quiet = 10;
        BitArray row = new BitArray(modules.length() + 2 * quiet);
        for (int i = 0; i < modules.length(); i++) {
            if (modules.charAt(i) == '1') {
                row.set(i + quiet);
            }
        }
        return new Code128Reader().decodeRow(0, row, null).getText();
    }
}
