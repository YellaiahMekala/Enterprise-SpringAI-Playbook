package com.springai.explore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authenticates a request from the {@code X-API-Key} header.
 * <p>
 * Only SHA-256 digests of the configured keys are kept in memory, and digests are compared in constant time,
 * so a timing side channel cannot be used to guess a key. The matching caller id becomes the principal name.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    /** Matches the VARCHAR(36) conversation_id column of the Spring AI JDBC chat memory table. */
    static final int MAX_CALLER_ID_LENGTH = 36;

    private final Map<String, byte[]> digestsByCaller = new LinkedHashMap<>();

    /** @param apiKeys caller id to API key */
    public ApiKeyAuthenticationFilter(Map<String, String> apiKeys) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            throw new IllegalStateException("No API keys configured: set app.security.api-keys.<caller-id> (env APP_API_KEY).");
        }
        apiKeys.forEach((caller, key) -> {
            if (caller.isBlank() || caller.length() > MAX_CALLER_ID_LENGTH) {
                throw new IllegalStateException("API caller id must be 1-" + MAX_CALLER_ID_LENGTH + " characters: " + caller);
            }
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("API key for caller '" + caller + "' is empty: set APP_API_KEY.");
            }
            byte[] digest = sha256(key);
            if (digestsByCaller.values().stream().anyMatch(existing -> MessageDigest.isEqual(existing, digest))) {
                throw new IllegalStateException("API key for caller '" + caller
                        + "' is already assigned to another caller. Configure a unique key for each caller.");
            }
            digestsByCaller.put(caller, digest);
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && !presented.isBlank()) {
            String caller = resolveCaller(presented);
            if (caller != null) {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        caller, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveCaller(String presentedKey) {
        byte[] presented = sha256(presentedKey);
        String match = null;
        // Scan every entry (no early exit) so timing does not reveal which entry matched.
        for (var entry : digestsByCaller.entrySet()) {
            if (MessageDigest.isEqual(entry.getValue(), presented)) {
                match = entry.getKey();
            }
        }
        return match;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
