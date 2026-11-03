package com.helmifr.library.book;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.author.Author;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Looking up a book by ISBN at Open Library to fill in the form. A local HTTP server stands in for
 * Open Library and answers in its documented "jscmd=data" format.
 */
@IntegrationTest
@DisplayName("ISBN lookup (Open Library)")
class IsbnLookupTest {

    private static final AtomicReference<String> ANSWER = new AtomicReference<>("{}");
    private static final AtomicReference<String> LAST_QUERY = new AtomicReference<>();
    private static final AtomicReference<Integer> STATUS = new AtomicReference<>(200);
    private static final HttpServer OPEN_LIBRARY = start();

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/books", exchange -> {
                LAST_QUERY.set(exchange.getRequestURI().getQuery());
                byte[] body = ANSWER.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(STATUS.get(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void openLibrary(DynamicPropertyRegistry registry) {
        registry.add("library.catalogue.lookup-url", () -> "http://127.0.0.1:" + OPEN_LIBRARY.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        OPEN_LIBRARY.stop(0);
    }

    @Autowired Api api;
    @Autowired TestData data;
    UserAccount librarian;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        STATUS.set(200);
    }

    @Test
    @DisplayName("fills title, author, publisher, year and known categories; matches an author already in the catalogue")
    void found() throws Exception {
        Author hirata = data.author("Andrea Hirata");
        ANSWER.set("""
                {"ISBN:9789793062792": {
                  "title": "Laskar Pelangi",
                  "authors": [{"name": "andrea hirata", "url": "https://openlibrary.org/authors/OL1A"}],
                  "publishers": [{"name": "Bentang Pustaka"}],
                  "publish_date": "September 2005",
                  "subjects": [{"name": "Fiction"}, {"name": "Belitung Island (Indonesia)"}, {"name": "young adult"}],
                  "cover": {"medium": "https://covers.openlibrary.org/b/id/1-M.jpg"}
                }}""");

        api.as(librarian).get("/api/books/lookup", "isbn", "979-3062-79-7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("9789793062792"))
                .andExpect(jsonPath("$.title").value("Laskar Pelangi"))
                .andExpect(jsonPath("$.authorName").value("andrea hirata"))
                .andExpect(jsonPath("$.authorId").value(hirata.getId()))
                .andExpect(jsonPath("$.publisherName").value("Bentang Pustaka"))
                .andExpect(jsonPath("$.publishedDate").value("2005-01-01"))
                .andExpect(jsonPath("$.categories[0]").value("Fiction"))
                .andExpect(jsonPath("$.categories[1]").value("Young Adult"))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.coverUrl")
                        .value("https://covers.openlibrary.org/b/isbn/9789793062792-M.jpg?default=false"));
        org.assertj.core.api.Assertions.assertThat(LAST_QUERY.get())
                .contains("bibkeys=ISBN:9789793062792").contains("format=json").contains("jscmd=data");
    }

    @Test
    @DisplayName("an author not yet in the catalogue comes back by name only")
    void unknownAuthor() throws Exception {
        ANSWER.set("""
                {"ISBN:9789793062792": {"title": "Laskar Pelangi", "authors": [{"name": "Andrea Hirata"}]}}""");

        api.as(librarian).get("/api/books/lookup", "isbn", "9789793062792")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("Andrea Hirata"))
                .andExpect(jsonPath("$.authorId").doesNotExist())
                .andExpect(jsonPath("$.publishedDate").doesNotExist());
    }

    @Test
    @DisplayName("an ISBN Open Library does not know is a 404 with a clear message")
    void notFound() throws Exception {
        ANSWER.set("{}");

        api.as(librarian).get("/api/books/lookup", "isbn", "9789793062792")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Open Library has no book with ISBN 9789793062792."));
    }

    @Test
    @DisplayName("an invalid ISBN is refused before calling Open Library")
    void invalidIsbn() throws Exception {
        LAST_QUERY.set(null);

        api.as(librarian).get("/api/books/lookup", "isbn", "12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isbn").exists());
        org.assertj.core.api.Assertions.assertThat(LAST_QUERY.get()).isNull();
    }

    @Test
    @DisplayName("when Open Library is down the librarian is told to fill the form by hand")
    void serviceDown() throws Exception {
        STATUS.set(500);

        api.as(librarian).get("/api/books/lookup", "isbn", "9789793062792")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Open Library could not be reached. Please fill in the details by hand."));
    }

    @Test
    @DisplayName("only librarians can use the lookup")
    void librariansOnly() throws Exception {
        api.as(data.customer("Ani")).get("/api/books/lookup", "isbn", "9789793062792").andExpect(status().isForbidden());
    }
}
