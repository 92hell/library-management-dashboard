package com.helmifr.library.author;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@DisplayName("Authors API")
class AuthorApiTest {

    @Autowired Api api;
    @Autowired TestData data;
    UserAccount librarian;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
    }

    @Test
    @DisplayName("update targets the id in the URL; ids and flags in the body are ignored")
    void updateUsesPathId() throws Exception {
        Author author = data.author("Pramoedya");

        api.as(librarian).put("/api/authors/" + author.getId(), """
                        {"id": 999, "name": "Pramoedya Ananta Toer", "deleted": true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(author.getId()))
                .andExpect(jsonPath("$.name").value("Pramoedya Ananta Toer"));
        api.as(librarian).get("/api/authors/" + author.getId()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("updating an unknown author is a 404, not an accidental insert")
    void updateUnknownIs404() throws Exception {
        api.as(librarian).put("/api/authors/424242", "{\"name\": \"Ghost\"}").andExpect(status().isNotFound());
        api.as(librarian).get("/api/authors").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("deleting is a soft delete: the author disappears from reads")
    void softDelete() throws Exception {
        Author author = data.author("Chairil Anwar");

        api.as(librarian).delete("/api/authors/" + author.getId()).andExpect(status().isNoContent());

        api.as(librarian).get("/api/authors/" + author.getId()).andExpect(status().isNotFound());
        api.as(librarian).get("/api/authors").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("an author who still has books cannot be deleted (409)")
    void cannotDeleteAuthorWithBooks() throws Exception {
        Author author = data.author("Andrea Hirata");
        data.book("Laskar Pelangi", author);

        api.as(librarian).delete("/api/authors/" + author.getId()).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("invalid input returns 400 with an error per field")
    void validation() throws Exception {
        api.as(librarian).post("/api/authors", "{\"name\": \"  \", \"dateOfBirth\": \"2999-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
        api.as(librarian).post("/api/authors", "{not json").andExpect(status().isBadRequest());
    }
}
