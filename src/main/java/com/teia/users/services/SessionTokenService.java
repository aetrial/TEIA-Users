package com.teia.users.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Service for generating and validating short-lived session tokens for WebSocket authentication.
 * Tokens are HMAC-SHA256 signed and valid for a configurable duration (default 60 seconds).
 */
@Service
public class SessionTokenService {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TOKEN_SEPARATOR = ".";

    @Value("${internal.secret}")
    private String internalSecret;

    @Value("${teia.session-token.validity-seconds:60}")
    private long tokenValiditySeconds;

    /**
     * Generates a short-lived session token for WebSocket authentication.
     * Token format: base64(clientId.timestamp).base64(hmac)
     *
     * @param clientId  The client ID
     * @param sessionId The session ID
     * @return The signed session token
     */
    public String generateToken(Long clientId, String sessionId) {
        long timestamp = System.currentTimeMillis();
        String payload = clientId + TOKEN_SEPARATOR + sessionId + TOKEN_SEPARATOR + timestamp;

        try {
            String signature = computeHmacSha256(payload, internalSecret);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                    + TOKEN_SEPARATOR
                    + signature;

            log.debug("[SessionTokenService] Generated token for client {} session {} (valid for {}s)",
                    clientId, sessionId, tokenValiditySeconds);

            return token;
        } catch (Exception e) {
            log.error("[SessionTokenService] Failed to generate token: {}", e.getMessage());
            throw new TokenGenerationException("Failed to generate session token", e);
        }
    }

    /**
     * Validates a session token.
     *
     * @param token    The token to validate
     * @param clientId The expected client ID
     * @return TokenValidationResult containing validation status and extracted data
     */
    public TokenValidationResult validateToken(String token, Long clientId) {
        if (token == null || token.isBlank()) {
            log.warn("[SessionTokenService] Token is null or blank");
            return TokenValidationResult.invalid("Token is required");
        }

        String[] parts = token.split("\\" + TOKEN_SEPARATOR, 2);
        if (parts.length != 2) {
            log.warn("[SessionTokenService] Invalid token format");
            return TokenValidationResult.invalid("Invalid token format");
        }

        String encodedPayload = parts[0];
        String providedSignature = parts[1];

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("[SessionTokenService] Failed to decode token payload");
            return TokenValidationResult.invalid("Invalid token encoding");
        }

        String[] payloadParts = payload.split("\\" + TOKEN_SEPARATOR);
        if (payloadParts.length != 3) {
            log.warn("[SessionTokenService] Invalid token payload structure");
            return TokenValidationResult.invalid("Invalid token structure");
        }

        Long tokenClientId;
        String sessionId;
        long timestamp;

        try {
            tokenClientId = Long.parseLong(payloadParts[0]);
            sessionId = payloadParts[1];
            timestamp = Long.parseLong(payloadParts[2]);
        } catch (NumberFormatException e) {
            log.warn("[SessionTokenService] Invalid token payload values");
            return TokenValidationResult.invalid("Invalid token values");
        }

        // Verify client ID matches
        if (!tokenClientId.equals(clientId)) {
            log.warn("[SessionTokenService] Token client ID mismatch: expected={}, got={}", clientId, tokenClientId);
            return TokenValidationResult.invalid("Client ID mismatch");
        }

        // Verify signature
        try {
            String expectedSignature = computeHmacSha256(payload, internalSecret);
            if (!constantTimeEquals(providedSignature, expectedSignature)) {
                log.warn("[SessionTokenService] Invalid token signature for client {}", clientId);
                return TokenValidationResult.invalid("Invalid signature");
            }
        } catch (Exception e) {
            log.error("[SessionTokenService] Failed to verify signature: {}", e.getMessage());
            return TokenValidationResult.invalid("Signature verification failed");
        }

        // Verify token is not expired
        long now = System.currentTimeMillis();
        long age = now - timestamp;
        long maxAgeMs = tokenValiditySeconds * 1000;

        if (age > maxAgeMs) {
            log.warn("[SessionTokenService] Token expired: age={}ms, max={}ms", age, maxAgeMs);
            return TokenValidationResult.expired(sessionId);
        }

        if (age < 0) {
            // Token from the future - likely clock skew
            log.warn("[SessionTokenService] Token timestamp is in the future by {}ms", Math.abs(age));
            return TokenValidationResult.invalid("Invalid token timestamp");
        }

        log.debug("[SessionTokenService] Token validated for client {} session {}", clientId, sessionId);
        return TokenValidationResult.valid(sessionId, tokenClientId);
    }

    /**
     * Computes HMAC-SHA256 signature as a hex string.
     */
    private static String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    /**
     * Converts byte array to lowercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Result of token validation.
     */
    public static class TokenValidationResult {
        private final boolean valid;
        private final boolean expired;
        private final String sessionId;
        private final Long clientId;
        private final String errorMessage;

        private TokenValidationResult(boolean valid, boolean expired, String sessionId, Long clientId, String errorMessage) {
            this.valid = valid;
            this.expired = expired;
            this.sessionId = sessionId;
            this.clientId = clientId;
            this.errorMessage = errorMessage;
        }

        public static TokenValidationResult valid(String sessionId, Long clientId) {
            return new TokenValidationResult(true, false, sessionId, clientId, null);
        }

        public static TokenValidationResult invalid(String errorMessage) {
            return new TokenValidationResult(false, false, null, null, errorMessage);
        }

        public static TokenValidationResult expired(String sessionId) {
            return new TokenValidationResult(false, true, sessionId, null, "Token expired");
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isExpired() {
            return expired;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Long getClientId() {
            return clientId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static class TokenGenerationException extends RuntimeException {
        public TokenGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
