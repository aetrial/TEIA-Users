package com.teia.users.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link InternalRequestFilter}.
 *
 * Uses InternalTestController (test-only @RestController) to expose /internal/test
 * so the full security filter chain runs end-to-end through MockMvc.
 *
 * application-test.properties must contain: internal.secret=test-secret-key
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("InternalRequestFilter")
class InternalRequestFilterTest {

    private static final String TEST_SECRET   = "test-secret-key";
    private static final String TEST_CLIENT   = "teia-core";
    private static final String TEST_ENDPOINT = "/internal/test";

    @Autowired
    private MockMvc mockMvc;

    // -------------------------------------------------------------------------
    // Helper – compute HMAC-SHA256 identical to InternalRequestFilter
    // -------------------------------------------------------------------------
    private static String hmac(String bodyString, String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((bodyString + timestamp).getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    private static String now() {
        return String.valueOf(System.currentTimeMillis());
    }

    // =========================================================================
    // 1. POST without any auth headers → 401 Missing required authentication headers
    // =========================================================================
    @Test
    @DisplayName("POST without any auth headers returns 401 'Missing required authentication headers'")
    void noHeaders_returns401() throws Exception {
        mockMvc.perform(post(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing required authentication headers"));
    }

    // =========================================================================
    // 2. POST with correct signature, valid timestamp, valid client ID → 200
    // =========================================================================
    @Test
    @DisplayName("POST with correct signature, valid timestamp, valid client ID returns 200")
    void validRequest_returns200() throws Exception {
        String body      = "{\"action\":\"ping\"}";
        String timestamp = now();
        String sig       = hmac(body, timestamp);

        mockMvc.perform(post(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Internal-Key", sig)
                .header("X-Timestamp",    timestamp)
                .header("X-Client-ID",    TEST_CLIENT))
            .andExpect(status().isOk());
    }

    // =========================================================================
    // 3. POST with tampered body → 401 Invalid signature
    // =========================================================================
    @Test
    @DisplayName("POST with tampered body returns 401 'Invalid signature'")
    void tamperedBody_returns401InvalidSignature() throws Exception {
        String signedBody = "{\"action\":\"ping\"}";
        String sentBody   = "{\"action\":\"pwned\"}";   // different from signed body
        String timestamp  = now();
        String sig        = hmac(signedBody, timestamp);

        mockMvc.perform(post(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sentBody)
                .header("X-Internal-Key", sig)
                .header("X-Timestamp",    timestamp)
                .header("X-Client-ID",    TEST_CLIENT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid signature"));
    }

    // =========================================================================
    // 4. POST with expired timestamp (> 30 s old) → 401 Request timestamp expired
    // =========================================================================
    @Test
    @DisplayName("POST with expired timestamp (31 s ago) returns 401 'Request timestamp expired'")
    void expiredTimestamp_returns401() throws Exception {
        String body      = "{}";
        String timestamp = String.valueOf(System.currentTimeMillis() - 31_000L);
        String sig       = hmac(body, timestamp);

        mockMvc.perform(post(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Internal-Key", sig)
                .header("X-Timestamp",    timestamp)
                .header("X-Client-ID",    TEST_CLIENT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Request timestamp expired"));
    }

    // =========================================================================
    // 5. POST with future timestamp (> 30 s ahead) → 401 Request timestamp expired
    // =========================================================================
    @Test
    @DisplayName("POST with future timestamp (31 s ahead) returns 401 'Request timestamp expired'")
    void futureTimestamp_returns401() throws Exception {
        String body      = "{}";
        String timestamp = String.valueOf(System.currentTimeMillis() + 31_000L);
        String sig       = hmac(body, timestamp);

        mockMvc.perform(post(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Internal-Key", sig)
                .header("X-Timestamp",    timestamp)
                .header("X-Client-ID",    TEST_CLIENT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Request timestamp expired"));
    }

    // =========================================================================
    // 6. POST without X-Internal-Key only → 401
    // =========================================================================
    @Test
    @DisplayName("POST without X-Internal-Key returns 401")
    void missingInternalKey_returns401() throws Exception {
        String timestamp = now();

        mockMvc.perform(post(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Timestamp", timestamp)
                .header("X-Client-ID", TEST_CLIENT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing required authentication headers"));
    }

    // =========================================================================
    // 7. POST without X-Timestamp only → 401
    // =========================================================================
    @Test
    @DisplayName("POST without X-Timestamp returns 401")
    void missingTimestamp_returns401() throws Exception {
        mockMvc.perform(post(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Internal-Key", "somesignature")
                .header("X-Client-ID",    TEST_CLIENT))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing required authentication headers"));
    }

    // =========================================================================
    // 8. POST without X-Client-ID only → 401
    // =========================================================================
    @Test
    @DisplayName("POST without X-Client-ID returns 401")
    void missingClientId_returns401() throws Exception {
        String timestamp = now();
        String sig       = hmac("{}", timestamp);

        mockMvc.perform(post(TEST_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Internal-Key", sig)
                .header("X-Timestamp",    timestamp))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing required authentication headers"));
    }
}
