package com.helmifr.library.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Keeps hosting bandwidth low (free plans meter outbound traffic): responses are gzipped, and the
 * content-hashed build assets are cached by browsers for a year, while the page itself is always
 * revalidated so a new deploy shows up immediately. Real HTTP, because compression is done by Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=${test.isolated-db.prefix}caching${test.isolated-db.suffix}")
@DisplayName("Compression and browser caching")
class CompressionAndCachingTest {

    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<byte[]> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept-Encoding", "gzip").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    @DisplayName("built assets are gzipped and cached for a year")
    void assetsCompressedAndCachedLong() throws Exception {
        HttpResponse<byte[]> js = get("/assets/app-test123.js");

        assertThat(js.statusCode()).isEqualTo(200);
        assertThat(js.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(js.headers().firstValue("Cache-Control").orElse(""))
                .contains("max-age=31536000").contains("immutable");
    }

    @Test
    @DisplayName("the page itself is gzipped but always revalidated, so deploys show up at once")
    void pageNotCachedLong() throws Exception {
        HttpResponse<byte[]> page = get("/books");

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(page.headers().firstValue("Cache-Control").orElse("")).contains("no-cache")
                .doesNotContain("max-age=31536000");
    }
}
