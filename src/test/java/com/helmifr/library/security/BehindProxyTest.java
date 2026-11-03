package com.helmifr.library.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
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
 * Real HTTP through Tomcat, as behind a hosting proxy (Render, Railway): the proxy connects from a
 * private address and passes the visitor's address and the original scheme in X-Forwarded-*.
 * (Security review finding: every visitor looked like the proxy, so one attacker could lock out
 * everyone, and HTTPS was never detected.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:proxy;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DisplayName("Running behind a reverse proxy")
class BehindProxyTest {

    @LocalServerPort int port;

    /** A browser-like client: keeps cookies and echoes the XSRF token. */
    private final class Visitor {
        final CookieManager cookies = new CookieManager();
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        final String address;

        Visitor(String address) {
            this.address = address;
        }

        HttpResponse<String> get(String path, String... headers) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("X-Forwarded-For", address);
            for (int i = 0; i + 1 < headers.length; i += 2) {
                b.header(headers[i], headers[i + 1]);
            }
            return http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> login(String username, String password) throws Exception {
            get("/api/auth/me");
            String xsrf = cookies.getCookieStore().getCookies().stream()
                    .filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElseThrow();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                    .header("X-Forwarded-For", address)
                    .header("X-XSRF-TOKEN", xsrf)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    @DisplayName("the throttle counts the real visitor address, so an attacker cannot lock out others")
    void throttleUsesForwardedAddress() throws Exception {
        Visitor attacker = new Visitor("203.0.113.5");
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            assertThat(attacker.login("librarian", "wrong-guess").statusCode()).isEqualTo(401);
        }
        assertThat(attacker.login("librarian", "librarian-test-pw").statusCode()).isEqualTo(429);

        Visitor owner = new Visitor("198.51.100.7");
        assertThat(owner.login("librarian", "librarian-test-pw").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("HTTPS at the proxy is detected: HSTS is sent and the session cookie is Secure")
    void httpsDetected() throws Exception {
        Visitor visitor = new Visitor("198.51.100.8");
        HttpResponse<String> demo = visitor.get("/api/auth/demo", "X-Forwarded-Proto", "https");
        assertThat(demo.headers().firstValue("Strict-Transport-Security")).isPresent();

        HttpResponse<String> login = visitor.login("librarian", "librarian-test-pw");
        assertThat(login.headers().allValues("Set-Cookie"))
                .anySatisfy(c -> assertThat(c).startsWith("JSESSIONID=").containsIgnoringCase("Secure"));
    }
}
