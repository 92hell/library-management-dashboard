package com.helmifr.library.book;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmifr.library.account.UserAccount;
import com.helmifr.library.author.Author;
import com.helmifr.library.member.Member;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A title can have several physical copies, each with a barcode; loans are for one copy. */
@IntegrationTest
@DisplayName("Copies and barcodes")
class CopiesTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired CopyRepository copies;
    UserAccount librarian;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
    }

    private String lend(Long bookId, Member m) {
        return "{\"bookId\": %d, \"memberId\": %d}".formatted(bookId, m.getId());
    }

    private String lendByBarcode(String barcode, Member m) {
        return "{\"copyBarcode\": \"%s\", \"memberId\": %d}".formatted(barcode, m.getId());
    }

    private String firstBarcode(Book book) {
        return copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId()).get(0).getBarcode();
    }

    @Test
    @DisplayName("a new book can be added with several copies, each with its own barcode")
    void createWithCopies() throws Exception {
        Author author = data.author("Andrea Hirata");
        String json = api.as(librarian).post("/api/books", """
                        {"title": "Laskar Pelangi", "publishedDate": "2005-01-01", "authorId": %d, "copies": 3}"""
                        .formatted(author.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCopies").value(3))
                .andExpect(jsonPath("$.availableCopies").value(3))
                .andReturn().getResponse().getContentAsString();
        long id = new ObjectMapper().readTree(json).get("id").asLong();

        api.as(librarian).get("/api/books/" + id + "/copies")
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].barcode", startsWith("BC-")))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("a title with 2 copies can be lent to two members at once, not three")
    void lendEachCopyOnce() throws Exception {
        Book book = data.bookWithCopies("Bumi Manusia", 2);

        api.as(librarian).post("/api/borrowed-books", lend(book.getId(), data.member("Ani"))).andExpect(status().isCreated());
        api.as(librarian).get("/api/books/" + book.getId())
                .andExpect(jsonPath("$.availableCopies").value(1))
                .andExpect(jsonPath("$.onLoan").value(false));
        api.as(librarian).post("/api/borrowed-books", lend(book.getId(), data.member("Bayu"))).andExpect(status().isCreated());
        api.as(librarian).post("/api/borrowed-books", lend(book.getId(), data.member("Citra")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No copy of \"Bumi Manusia\" is available."));
        api.as(librarian).get("/api/books/" + book.getId())
                .andExpect(jsonPath("$.availableCopies").value(0))
                .andExpect(jsonPath("$.onLoan").value(true));
    }

    @Test
    @DisplayName("desk checkout by barcode lends exactly that copy; a copy already out is refused")
    void checkoutByBarcode() throws Exception {
        Book book = data.bookWithCopies("Saman", 2);
        String barcode = copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId()).get(1).getBarcode();

        api.as(librarian).post("/api/borrowed-books", lendByBarcode(barcode.toLowerCase(), data.member("Ani")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.copy.barcode").value(barcode))
                .andExpect(jsonPath("$.book.title").value("Saman"));
        api.as(librarian).post("/api/borrowed-books", lendByBarcode(barcode, data.member("Bayu")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Copy " + barcode + " is already on loan."));
        api.as(librarian).post("/api/borrowed-books", lendByBarcode("NOPE-404", data.member("Bayu")))
                .andExpect(status().isNotFound());
        api.as(librarian).post("/api/borrowed-books", "{\"memberId\": %d}".formatted(data.member("Dewi").getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bookId").exists());
    }

    @Test
    @DisplayName("returning by barcode closes that copy's loan; unknown or idle copies are reported")
    void returnByBarcode() throws Exception {
        Book book = data.book("Cantik Itu Luka");
        data.activeLoan(book, data.member("Ani"), TODAY.minusDays(3), TODAY.plusDays(11));
        String barcode = firstBarcode(book);

        api.as(librarian).post("/api/borrowed-books/return-by-barcode", "{\"barcode\": \"%s\"}".formatted(barcode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.member.name").value("Ani"));
        api.as(librarian).post("/api/borrowed-books/return-by-barcode", "{\"barcode\": \"%s\"}".formatted(barcode))
                .andExpect(status().isConflict());
        api.as(librarian).post("/api/borrowed-books/return-by-barcode", "{\"barcode\": \"NOPE-404\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("looking up a barcode shows the title and who has the copy")
    void lookup() throws Exception {
        Book book = data.book("Ronggeng");
        data.activeLoan(book, data.member("Rina"), TODAY, TODAY.plusDays(14));

        api.as(librarian).get("/api/copies/lookup", "barcode", firstBarcode(book))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.book.title").value("Ronggeng"))
                .andExpect(jsonPath("$.status").value("ON_LOAN"))
                .andExpect(jsonPath("$.loan.memberName").value("Rina"));
    }

    @Test
    @DisplayName("librarians add, relabel and withdraw copies; barcodes are unique and a lent copy stays")
    void manageCopies() throws Exception {
        Book book = data.book("Supernova");
        data.activeLoan(book, data.member("Ani"), TODAY, TODAY.plusDays(14));
        Long lentCopy = copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId()).get(0).getId();

        String json = api.as(librarian).post("/api/books/" + book.getId() + "/copies",
                        "{\"barcode\": \"sup-002\", \"shelfLocation\": \"FIC LES\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barcode").value("SUP-002"))
                .andReturn().getResponse().getContentAsString();
        long newCopy = new ObjectMapper().readTree(json).get("id").asLong();
        api.as(librarian).post("/api/books/" + book.getId() + "/copies", "{\"barcode\": \"SUP-002\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.barcode").exists());
        api.as(librarian).post("/api/books/" + book.getId() + "/copies", "{\"barcode\": \"bad barcode!\"}")
                .andExpect(status().isBadRequest());
        api.as(librarian).put("/api/copies/" + newCopy, "{\"barcode\": \"SUP-002\", \"shelfLocation\": \"FIC LES 2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shelfLocation").value("FIC LES 2"));

        api.as(librarian).delete("/api/copies/" + lentCopy).andExpect(status().isConflict());
        api.as(librarian).delete("/api/copies/" + newCopy).andExpect(status().isNoContent());
        api.as(librarian).get("/api/books/" + book.getId()).andExpect(jsonPath("$.totalCopies").value(1));
    }

    @Test
    @DisplayName("approving a request hands over an available copy")
    void approvalAssignsCopy() throws Exception {
        UserAccount customer = data.customer("Siti");
        Book book = data.book("Laskar Pelangi");
        var request = data.pendingRequest(book, customer.getMember());

        api.as(librarian).post("/api/borrowed-books/" + request.getId() + "/approve", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copy.barcode").value(firstBarcode(book)));
    }

    @Test
    @DisplayName("loans can be searched by barcode")
    void searchByBarcode() throws Exception {
        Book book = data.book("Sang Pemimpi");
        data.activeLoan(book, data.member("Ani"), TODAY, TODAY.plusDays(14));
        data.activeLoan(data.book("Other"), data.member("Bayu"), TODAY, TODAY.plusDays(14));

        api.as(librarian).get("/api/borrowed-books", "q", firstBarcode(book))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].book.title").value("Sang Pemimpi"));
    }

    @Test
    @DisplayName("customers see how many copies are on the shelf, but not the copies themselves")
    void customerView() throws Exception {
        Book book = data.bookWithCopies("Anak Semua Bangsa", 3);
        data.activeLoan(book, data.member("Ani"), TODAY, TODAY.plusDays(14));
        UserAccount customer = data.customer("Siti");

        api.as(customer).get("/api/books/" + book.getId())
                .andExpect(jsonPath("$.availableCopies").value(2))
                .andExpect(jsonPath("$.totalCopies").value(3));
        api.as(customer).get("/api/books/" + book.getId() + "/copies").andExpect(status().isForbidden());
        api.as(customer).get("/api/copies/lookup", "barcode", firstBarcode(book)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleting a book withdraws its copies")
    void deleteBookWithdrawsCopies() throws Exception {
        Book book = data.bookWithCopies("Old Title", 2);

        api.as(librarian).delete("/api/books/" + book.getId()).andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId())).isEmpty();
    }
}
