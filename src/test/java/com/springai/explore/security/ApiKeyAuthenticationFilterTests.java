package com.springai.explore.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyAuthenticationFilterTests {

    private final ApiKeyAuthenticationFilter filter =
            new ApiKeyAuthenticationFilter(Map.of("alice", "key-alice", "bob", "key-bob"));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void run(String headerValue) throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader(ApiKeyAuthenticationFilter.HEADER, headerValue);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void validKeyAuthenticatesTheMatchingCaller() throws Exception {
        var captured = new String[1];
        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthenticationFilter.HEADER, "key-bob");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                captured[0] = SecurityContextHolder.getContext().getAuthentication().getName();
            }
        });
        assertThat(captured[0]).isEqualTo("bob");
    }

    @Test
    void wrongKeyLeavesRequestUnauthenticated() throws Exception {
        run("not-a-key");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingOrBlankHeaderLeavesRequestUnauthenticated() throws Exception {
        run(null);
        run("  ");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void startupFailsWhenNoKeysAreConfigured() {
        assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupFailsWhenAKeyIsBlank() {
        assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(Map.of("alice", " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alice");
    }

    @Test
    void startupFailsWhenCallerIdExceedsMemoryColumnWidth() {
        assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(Map.of("x".repeat(37), "k")))
                .isInstanceOf(IllegalStateException.class);
    }
}
