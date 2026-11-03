package com.helmifr.library.common;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helmifr.library.support.Api;
import com.helmifr.library.support.IntegrationTest;
import com.helmifr.library.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Regression: the old app served JSON when a page such as /books was reloaded. */
@IntegrationTest
@DisplayName("Single-page app routing")
class SpaRoutingTest {

    @Autowired Api api;
    @Autowired TestData data;

    @Test
    @DisplayName("client-side routes (one or two segments) serve index.html")
    void clientRoutes() throws Exception {
        api.anonymous().get("/books").andExpect(forwardedUrl("/index.html"));
        api.anonymous().get("/books/42").andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("assets and unknown API paths are real 404s, never the app")
    void assetsAndApiAreNotForwarded() throws Exception {
        api.anonymous().get("/assets/missing.js").andExpect(status().isNotFound());
        api.as(data.librarian("Lina")).get("/api/nope").andExpect(status().isNotFound());
    }
}
