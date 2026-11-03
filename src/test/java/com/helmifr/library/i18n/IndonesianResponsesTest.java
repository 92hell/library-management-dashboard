package com.helmifr.library.i18n;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.book.Book;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

/** With "Accept-Language: id" (sent by the app in Indonesian), errors and notifications come in Bahasa Indonesia. */
@IntegrationTest
@DisplayName("Server responses in Bahasa Indonesia")
class IndonesianResponsesTest {

    @Autowired MockMvc mvc;
    @Autowired TestData data;
    @Autowired com.helmifr.library.support.Api api;
    UserAccount librarian;
    UserAccount siti;
    Book book;

    @BeforeEach
    void setUp() {
        librarian = data.librarian("Lina");
        siti = data.customer("Siti");
        book = data.book("Bumi Manusia");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder indonesian(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, UserAccount who) {
        return request.header("Accept-Language", "id")
                .with(SecurityMockMvcRequestPostProcessors.user(new com.helmifr.library.security.AccountPrincipal(who)))
                .with(SecurityMockMvcRequestPostProcessors.csrf());
    }

    @Test
    @DisplayName("a refused action explains itself in Indonesian; without the header it stays English")
    void conflictInIndonesian() throws Exception {
        data.activeLoan(book, siti.getMember(), LocalDate.now(), LocalDate.now().plusDays(14));
        String body = "{\"bookId\": %d, \"memberId\": %d}".formatted(book.getId(), data.member("Bayu").getId());

        mvc.perform(indonesian(post("/api/borrowed-books"), librarian).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("Tidak ada eksemplar")));
        api.as(librarian).post("/api/borrowed-books", body)
                .andExpect(jsonPath("$.detail", containsString("No copy of")));
    }

    @Test
    @DisplayName("not found and field validation messages are in Indonesian")
    void notFoundAndValidation() throws Exception {
        mvc.perform(indonesian(get("/api/books/424242"), librarian))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Buku 424242 tidak ditemukan"));
        mvc.perform(indonesian(post("/api/authors"), librarian).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validasi gagal"))
                .andExpect(jsonPath("$.errors.name").value("tidak boleh kosong"));
    }

    @Test
    @DisplayName("notifications are listed in the reader's language, with dates written the Indonesian way")
    void notificationsInIndonesian() throws Exception {
        var loan = data.pendingRequest(book, siti.getMember());
        api.as(librarian).post("/api/borrowed-books/" + loan.getId() + "/approve", "{}").andExpect(status().isOk());
        LocalDate until = LocalDate.now().plusDays(3);
        String indonesianDate = until.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy",
                java.util.Locale.forLanguageTag("id")));

        mvc.perform(indonesian(get("/api/notifications"), siti))
                .andExpect(jsonPath("$.content[0].message", containsString("Bumi Manusia")))
                .andExpect(jsonPath("$.content[0].message", containsString(indonesianDate)))
                .andExpect(jsonPath("$.content[0].message", containsString("disetujui")));
        api.as(siti).get("/api/notifications")
                .andExpect(jsonPath("$.content[0].message", containsString("was approved and is ready for pickup until " + until)));
    }

    @Test
    @DisplayName("login errors are in Indonesian")
    void loginInIndonesian() throws Exception {
        mvc.perform(post("/api/auth/login").header("Accept-Language", "id")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"nobody-here\", \"password\": \"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Nama pengguna atau kata sandi salah"));
    }

    @Test
    @DisplayName("statuses inside Indonesian messages are words, not codes")
    void statusWords() throws Exception {
        var loan = data.activeLoan(book, siti.getMember(), LocalDate.now(), LocalDate.now().plusDays(14));

        mvc.perform(indonesian(post("/api/borrowed-books/" + loan.getId() + "/approve"), librarian)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("dipinjam")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(containsString("ACTIVE"))));
    }
}
