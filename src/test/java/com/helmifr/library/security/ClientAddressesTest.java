package com.helmifr.library.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * On Render, requests pass Cloudflare, then Render's proxy. Tomcat (forward-headers-strategy
 * native) already resolves the address up to the hop before Render's proxy - a Cloudflare server.
 * The real visitor is then taken from what Cloudflare itself set, and only then.
 */
@DisplayName("Client address behind Cloudflare")
class ClientAddressesTest {

    private final ClientAddresses addresses = new ClientAddresses(List.of("162.158.0.0/15", "2400:cb00::/32"));

    private static MockHttpServletRequest from(String remoteAddr) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remoteAddr);
        return r;
    }

    @Test
    @DisplayName("through Cloudflare: the visitor from CF-Connecting-IP")
    void cloudflareHeader() {
        MockHttpServletRequest r = from("162.158.88.84");
        r.addHeader("CF-Connecting-IP", "203.0.113.50");
        assertThat(addresses.of(r)).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("through Cloudflare without that header: the entry Cloudflare appended to X-Forwarded-For")
    void cloudflareForwardedFor() {
        MockHttpServletRequest r = from("162.158.88.84");
        r.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.50");
        assertThat(addresses.of(r)).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("NOT through Cloudflare: a CF-Connecting-IP sent by the client is ignored (no spoofing)")
    void spoofedHeaderIgnored() {
        MockHttpServletRequest r = from("198.51.100.99");
        r.addHeader("CF-Connecting-IP", "1.1.1.1");
        r.addHeader("X-Forwarded-For", "1.1.1.1");
        assertThat(addresses.of(r)).isEqualTo("198.51.100.99");
    }

    @Test
    @DisplayName("garbage in the header is ignored; IPv6 Cloudflare servers are recognised too")
    void validation() {
        MockHttpServletRequest garbage = from("162.158.88.84");
        garbage.addHeader("CF-Connecting-IP", "not an ip\nAUDIT forged");
        assertThat(addresses.of(garbage)).isEqualTo("162.158.88.84");

        MockHttpServletRequest v6 = from("2400:cb00:2048:1::1");
        v6.addHeader("CF-Connecting-IP", "2001:db8::7");
        assertThat(addresses.of(v6)).isEqualTo("2001:db8::7");
    }
}
