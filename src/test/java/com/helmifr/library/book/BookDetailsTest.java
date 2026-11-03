package com.helmifr.library.book;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.author.Author;
import com.helmifr.library.loan.LoanStatus;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** ISBN, description and cover on a book, and the extra details on its own page (shelves, queue). */
@IntegrationTest
@DisplayName("Book details and covers")
class BookDetailsTest {

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired CopyRepository copies;
    UserAccount librarian;
    Author author;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        author = data.author("Andrea Hirata");
    }

    private String book(String isbn) {
        return """
                {"title": "Laskar Pelangi", "publishedDate": "2005-01-01", "authorId": %d, "isbn": %s,
                 "description": "Ten children at a village school on Belitung."}"""
                .formatted(author.getId(), isbn == null ? "null" : "\"" + isbn + "\"");
    }

    @Test
    @DisplayName("an ISBN-10 or hyphenated ISBN is stored as ISBN-13 and gives the book a cover")
    void isbnNormalisedWithCover() throws Exception {
        api.as(librarian).post("/api/books", book("979-3062-79-7"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value("9789793062792"))
                .andExpect(jsonPath("$.description").value("Ten children at a village school on Belitung."))
                .andExpect(jsonPath("$.coverUrl")
                        .value("https://covers.openlibrary.org/b/isbn/9789793062792-M.jpg?default=false"));
    }

    @Test
    @DisplayName("the catalogue can be searched by ISBN")
    void searchByIsbn() throws Exception {
        api.as(librarian).post("/api/books", book("9789793062792")).andExpect(status().isCreated());

        api.as(librarian).get("/api/books", "q", "9789793062792").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("a book without an ISBN has no cover URL (the app draws its own spine)")
    void noIsbnNoCover() throws Exception {
        api.as(librarian).post("/api/books", book(null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").doesNotExist())
                .andExpect(jsonPath("$.coverUrl").doesNotExist());
    }

    @Test
    @DisplayName("an ISBN with a wrong check digit, or one another book already has, is refused")
    void isbnValidated() throws Exception {
        api.as(librarian).post("/api/books", book("978-979-3062-79-3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isbn").value(containsString("not a valid ISBN")));
        api.as(librarian).post("/api/books", book("9789793062792")).andExpect(status().isCreated());
        api.as(librarian).post("/api/books", book("9793062797"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isbn").value(containsString("Another book")));
    }

    @Test
    @DisplayName("a book can be saved again with its own ISBN, and its ISBN can be removed")
    void updateKeepsOwnIsbn() throws Exception {
        String body = api.as(librarian).post("/api/books", book("9789793062792"))
                .andReturn().getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.parse(body).read("$.id", Long.class);

        api.as(librarian).put("/api/books/" + id, book("9789793062792")).andExpect(status().isOk());
        api.as(librarian).put("/api/books/" + id, book(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").doesNotExist());
    }

    @Test
    @DisplayName("a book's own page shows the shelves its copies are on and how many people are waiting")
    void shelvesAndQueue() throws Exception {
        Book book = data.bookWithCopies("Sang Pemimpi", 3);
        var list = copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(book.getId());
        list.get(0).setShelfLocation("FIC HIR");
        list.get(1).setShelfLocation("FIC HIR");
        list.get(2).setShelfLocation("Display");
        data.pendingRequest(book, data.member("Siti"));
        data.loan(book, data.member("Bayu"), LoanStatus.WAITLISTED, null, null, null);
        UserAccount customer = data.customer("Ani");

        api.as(customer).get("/api/books/" + book.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shelfLocations[0]").value("Display"))
                .andExpect(jsonPath("$.shelfLocations[1]").value("FIC HIR"))
                .andExpect(jsonPath("$.shelfLocations.length()").value(2))
                .andExpect(jsonPath("$.waiting").value(2));
    }

    @Test
    @DisplayName("customers can read a book's page but not change it")
    void customersReadOnly() throws Exception {
        Book book = data.book("Supernova", author);
        UserAccount customer = data.customer("Ani");

        api.as(customer).get("/api/books/" + book.getId()).andExpect(status().isOk());
        api.as(customer).put("/api/books/" + book.getId(), book(null)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the content security policy allows cover images from Open Library only")
    void cspAllowsCovers() throws Exception {
        api.anonymous().get("/api/auth/demo")
                .andExpect(header().string("Content-Security-Policy",
                        containsString("img-src 'self' data: https://covers.openlibrary.org https://archive.org https://*.archive.org")));
    }

    @Test
    @DisplayName("descriptions longer than 2,000 characters are refused")
    void descriptionLimit() throws Exception {
        api.as(librarian).post("/api/books", """
                        {"title": "X", "publishedDate": "%s", "authorId": %d, "description": "%s"}"""
                        .formatted(LocalDate.of(2001, 1, 1), author.getId(), "a".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());
    }
}
