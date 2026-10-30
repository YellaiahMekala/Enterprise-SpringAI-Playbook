package com.springai.explore.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.LinkedHashMap;
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

    @ParameterizedTest
    @ValueSource(strings = {"alice", "bob"})
    void validKeyAuthenticatesTheMatchingCaller(String caller) throws Exception {
        var captured = new String[1];
        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthenticationFilter.HEADER, "key-" + caller);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                captured[0] = SecurityContextHolder.getContext().getAuthentication().getName();
            }
        });
        assertThat(captured[0]).isEqualTo(caller);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "bob"})
    void startupRejectsDuplicateKeysRegardlessOfCallerOrderWithoutLeakingTheKey(String firstCaller) {
        String secondCaller = firstCaller.equals("alice") ? "bob" : "alice";
        String sharedKey = "shared-secret-must-not-appear";
        var keys = new LinkedHashMap<String, String>();
        keys.put(firstCaller, sharedKey);
        keys.put(secondCaller, sharedKey);

        assertThatThrownBy(() -> new ApiKeyAuthenticationFilter(keys))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(secondCaller)
                .hasMessageContaining("unique")
                .hasMessageNotContaining(sharedKey);
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
