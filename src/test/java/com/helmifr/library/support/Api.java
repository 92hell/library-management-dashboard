package com.helmifr.library.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.helmifr.library.account.UserAccount;
import com.helmifr.library.security.AccountPrincipal;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Small DSL over MockMvc, e.g. {@code api.as(customer).post("/api/my/loans", json)}.
 * Every request carries a valid CSRF token; {@link #anonymous()} makes signed-out calls.
 */
@TestComponent
public class Api {

    private final MockMvc mvc;

    public Api(MockMvc mvc) {
        this.mvc = mvc;
    }

    public Caller as(UserAccount account) {
        return new Caller(account);
    }

    /**
     * A visitor who is not signed in, from a network address of its own: per-address limits
     * (login lockout, sign-up cap) then never carry over from one test to another. Tests of those
     * limits set the address themselves.
     */
    public Caller anonymous() {
        return new Caller(null);
    }

    private static final java.util.concurrent.atomic.AtomicInteger VISITORS = new java.util.concurrent.atomic.AtomicInteger();

    public final class Caller {
        private final UserAccount account;

        private Caller(UserAccount account) {
            this.account = account;
        }

        /** @param params alternating query parameter names and values */
        public ResultActions get(String url, String... params) throws Exception {
            MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get(url);
            for (int i = 0; i + 1 < params.length; i += 2) {
                request.param(params[i], params[i + 1]);
            }
            return perform(request);
        }

        public ResultActions post(String url) throws Exception {
            return perform(MockMvcRequestBuilders.post(url));
        }

        public ResultActions post(String url, String json) throws Exception {
            return perform(withJson(MockMvcRequestBuilders.post(url), json));
        }

        public ResultActions put(String url, String json) throws Exception {
            return perform(withJson(MockMvcRequestBuilders.put(url), json));
        }

        public ResultActions delete(String url) throws Exception {
            return perform(MockMvcRequestBuilders.delete(url));
        }

        private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
            request.with(csrf());
            if (account != null) {
                request.with(user(new AccountPrincipal(account)));
            } else {
                int n = VISITORS.incrementAndGet();
                String address = "192.0.2." + (n % 250 + 1);
                String unique = "2001:db8::" + Integer.toHexString(n);
                request.with(r -> {
                    r.setRemoteAddr(n <= 250 ? address : unique);
                    return r;
                });
            }
            return mvc.perform(request);
        }

        private static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, String json) {
            return request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
    }
}
