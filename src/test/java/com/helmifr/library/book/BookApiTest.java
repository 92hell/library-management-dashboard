package com.helmifr.library.book;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.author.Author;
import com.helmifr.library.common.PagingConfig;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@DisplayName("Books API")
class BookApiTest {

    @Autowired Api api;
    @Autowired TestData data;
    UserAccount librarian;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
    }

    @Test
    @DisplayName("creating a book links its author and stores categories as a list")
    void create() throws Exception {
        Author author = data.author("Dee Lestari");

        api.as(librarian).post("/api/books", """
                        {"title": "Supernova", "publishedDate": "2001-01-01", "authorId": %d,
                         "categories": ["Fiction", "Science Fiction"]}""".formatted(author.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.name").value("Dee Lestari"))
                .andExpect(jsonPath("$.categories", hasSize(2)))
                .andExpect(jsonPath("$.onLoan").value(false));
    }

    @Test
    @DisplayName("unknown categories are rejected; an unknown author is a 404")
    void createValidation() throws Exception {
        Author author = data.author("Someone");

        api.as(librarian).post("/api/books", """
                        {"title": "X", "publishedDate": "2001-01-01", "authorId": %d, "categories": ["Nope"]}"""
                        .formatted(author.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.categories").exists());
        api.as(librarian).post("/api/books", "{\"title\": \"X\", \"publishedDate\": \"2001-01-01\", \"authorId\": 424242}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("search matches title, publisher, category or author and treats % and _ literally")
    void searchEscapesWildcards() throws Exception {
        Author author = data.author("Someone");
        data.book("100% Real", author);
        data.book("Plain Title", author);

        api.as(librarian).get("/api/books", "q", "%")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("100% Real"));
        api.as(librarian).get("/api/books", "q", "someone").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("page size is capped so clients cannot request unbounded pages")
    void pageSizeCap() throws Exception {
        api.as(librarian).get("/api/books", "size", "100000")
                .andExpect(jsonPath("$.size").value(PagingConfig.MAX_PAGE_SIZE));
    }

    @Test
    @DisplayName("books show onLoan, and a book on loan cannot be deleted (409)")
    void onLoanBookCannotBeDeleted() throws Exception {
        Book book = data.book("Cantik Itu Luka");
        data.activeLoan(book, data.member("Ani"), LocalDate.now(), LocalDate.now().plusDays(14));

        api.as(librarian).get("/api/books/" + book.getId()).andExpect(jsonPath("$.onLoan").value(true));
        api.as(librarian).delete("/api/books/" + book.getId()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("customers can browse the catalogue")
    void customersCanBrowse() throws Exception {
        data.book("Bumi Manusia");

        api.as(data.customer("Siti")).get("/api/books")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
