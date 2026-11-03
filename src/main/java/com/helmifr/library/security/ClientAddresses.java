package com.helmifr.library.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * The visitor's real address, for login throttling and the audit log.
 * <p>
 * Tomcat (server.forward-headers-strategy=native) already resolves X-Forwarded-For up to the hop
 * before the hosting platform's own proxy. On Render that hop is a Cloudflare edge server shared by
 * many visitors. Only when the request really came from such an edge server (its address is in
 * {@code library.security.edge-proxy-ranges}) is the visitor taken from what that edge set:
 * {@code CF-Connecting-IP}, or the last X-Forwarded-For entry it appended. Anyone else sending these
 * headers is ignored, so they cannot be spoofed.
 */
@Component
public class ClientAddresses {

    /** Cloudflare's published ranges (https://www.cloudflare.com/ips/). */
    static final String CLOUDFLARE_RANGES = "173.245.48.0/20,103.21.244.0/22,103.22.200.0/22,103.31.4.0/22,"
            + "141.101.64.0/18,108.162.192.0/18,190.93.240.0/20,188.114.96.0/20,197.234.240.0/22,198.41.128.0/17,"
            + "162.158.0.0/15,104.16.0.0/13,104.24.0.0/14,172.64.0.0/13,131.0.72.0/22,"
            + "2400:cb00::/32,2606:4700::/32,2803:f800::/32,2405:b500::/32,2405:8100::/32,2a06:98c0::/29,2c0f:f248::/32";

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:]{2,39}$");

    private final List<IpAddressMatcher> edgeProxies;

    @Autowired
    public ClientAddresses(@Value("${EDGE_PROXY_RANGES:${library.security.edge-proxy-ranges:" + CLOUDFLARE_RANGES + "}}") List<String> ranges) {
        this.edgeProxies = ranges.stream().map(String::trim).filter(r -> !r.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    public String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!fromEdgeProxy(remote)) {
            return remote;
        }
        String cf = request.getHeader("CF-Connecting-IP");
        if (isIp(cf)) {
            return cf.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].trim();
            if (isIp(last)) {
                return last;
            }
        }
        return remote;
    }

    private boolean fromEdgeProxy(String address) {
        if (!isIp(address)) {
            return false;
        }
        return edgeProxies.stream().anyMatch(m -> m.matches(address));
    }

    private static boolean isIp(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        return IPV4.matcher(v).matches() || (v.indexOf(':') >= 0 && IPV6.matcher(v).matches());
    }
}
