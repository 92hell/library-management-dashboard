package com.helmifr.library.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.helmifr.library.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Request IDs and access log")
class RequestLoggingTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("every response carries an X-Request-Id")
    void generatesRequestId() throws Exception {
        mvc.perform(get("/api/auth/demo")).andExpect(header().string("X-Request-Id", matchesPattern("[a-f0-9]{16}")));
    }

    @Test
    @DisplayName("a well-formed incoming X-Request-Id is reused; a malicious one is replaced")
    void propagatesOrReplaces() throws Exception {
        mvc.perform(get("/api/auth/demo").header("X-Request-Id", "edge-abc-123"))
                .andExpect(header().string("X-Request-Id", "edge-abc-123"));
        mvc.perform(get("/api/auth/demo").header("X-Request-Id", "bad\nid; injected"))
                .andExpect(header().string("X-Request-Id", matchesPattern("[a-f0-9]{16}")));
    }

    @Test
    @DisplayName("API calls are logged with method, path, status, duration and the request id")
    void accessLog(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/auth/me").header("X-Request-Id", "trace-me-1"));

        assertThat(output.getOut()).containsPattern("trace-me-1.*GET /api/auth/me -> 401 \\(\\d+ ms\\)");
    }
}
