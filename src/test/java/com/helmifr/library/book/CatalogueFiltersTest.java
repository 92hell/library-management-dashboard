package com.helmifr.library.book;

import static org.hamcrest.Matchers.contains;
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

/** Narrowing and ordering the catalogue: category, author, "on the shelf now", and sort order. */
@IntegrationTest
@DisplayName("Catalogue filters and sorting")
class CatalogueFiltersTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired Api api;
    @Autowired TestData data;
    @Autowired BookRepository books;
    @Autowired CopyRepository copies;
    UserAccount customer;
    Author hirata;
    Author dee;
    Book laskar;
    Book supernova;
    Book poems;

    @BeforeEach
    void setUp() {
        customer = data.customer("Siti");
        hirata = data.author("Andrea Hirata");
        dee = data.author("Dee Lestari");
        laskar = book("Laskar Pelangi", hirata, 2005, "Fiction,Young Adult");
        supernova = book("Supernova", dee, 2001, "Science Fiction");
        poems = book("Aku Ingin", dee, 2015, "Poetry, Fiction"); // older rows may have spaces after commas
    }

    private Book book(String title, Author author, int year, String categories) {
        Book b = data.book(title, author);
        b.setPublishedDate(LocalDate.of(year, 6, 1));
        b.setCategories(categories);
        return books.save(b);
    }

    private org.springframework.test.web.servlet.ResultActions list(String... params) throws Exception {
        return api.as(customer).get("/api/books", params);
    }

    @Test
    @DisplayName("a category matches whole category names only (\"Fiction\" is not \"Science Fiction\")")
    void category() throws Exception {
        list("category", "Fiction", "order", "title")
                .andExpect(jsonPath("$.content[*].title", contains("Aku Ingin", "Laskar Pelangi")));
        list("category", "Science Fiction").andExpect(jsonPath("$.content[*].title", contains("Supernova")));
    }

    @Test
    @DisplayName("an unknown category or sort order is a 400")
    void unknownValues() throws Exception {
        list("category", "Nope").andExpect(status().isBadRequest());
        list("order", "price").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("filters by author")
    void author() throws Exception {
        list("authorId", String.valueOf(dee.getId()), "order", "title")
                .andExpect(jsonPath("$.content[*].title", contains("Aku Ingin", "Supernova")));
    }

    @Test
    @DisplayName("\"on the shelf now\" leaves out titles whose every copy is lent or set aside")
    void availableNow() throws Exception {
        data.activeLoan(laskar, data.member("Bayu"), TODAY, TODAY.plusDays(14));
        var hold = data.loan(supernova, data.member("Ani"), LoanStatus.READY, null, null, null);
        hold.setCopy(copies.findAllByBookIdAndDeletedFalseOrderByIdAsc(supernova.getId()).get(0));
        data.copy(poems); // two copies, one lent: still on the shelf
        data.activeLoan(poems, data.member("Rina"), TODAY, TODAY.plusDays(14));

        list("available", "true").andExpect(jsonPath("$.content[*].title", contains("Aku Ingin")));
        list("available", "false").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("sorts by title, newest or oldest publication, or most recently added (the default)")
    void ordering() throws Exception {
        list("order", "title").andExpect(jsonPath("$.content[*].title", contains("Aku Ingin", "Laskar Pelangi", "Supernova")));
        list("order", "newest").andExpect(jsonPath("$.content[*].title", contains("Aku Ingin", "Laskar Pelangi", "Supernova")));
        list("order", "oldest").andExpect(jsonPath("$.content[*].title", contains("Supernova", "Laskar Pelangi", "Aku Ingin")));
        list().andExpect(jsonPath("$.content[*].title", contains("Aku Ingin", "Supernova", "Laskar Pelangi")));
    }

    @Test
    @DisplayName("filters combine with the search box and with each other")
    void combined() throws Exception {
        list("q", "super", "authorId", String.valueOf(dee.getId()))
                .andExpect(jsonPath("$.content[*].title", contains("Supernova")));
        list("category", "Fiction", "authorId", String.valueOf(hirata.getId()))
                .andExpect(jsonPath("$.content[*].title", contains("Laskar Pelangi")));
        list("category", "Poetry", "authorId", String.valueOf(hirata.getId())).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("the categories endpoint can list only categories that have books, for the filter menu")
    void categoriesInUse() throws Exception {
        api.as(customer).get("/api/books/categories", "inUse", "true")
                .andExpect(jsonPath("$", contains("Fiction", "Poetry", "Science Fiction", "Young Adult")));
    }
}
