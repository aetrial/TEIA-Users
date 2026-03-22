package com.teia.users.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionTokenService.
 * Verifies token generation, validation, and expiry behavior.
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionTokenServiceTest {

    @Autowired
    private SessionTokenService sessionTokenService;

    private static final Long TEST_CLIENT_ID = 123L;
    private static final String TEST_SESSION_ID = "sess_test123456";

    @BeforeEach
    void setUp() {
        // Ensure token validity is set to a reasonable test value
        ReflectionTestUtils.setField(sessionTokenService, "tokenValiditySeconds", 60L);
    }

    @Nested
    @DisplayName("Token Generation Tests")
    class TokenGenerationTests {

        @Test
        @DisplayName("Should generate a non-null token")
        void shouldGenerateNonNullToken() {
            String token = sessionTokenService.generateToken(TEST_CLIENT_ID, TEST_SESSION_ID);
            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("Should generate different tokens for different sessions")
        void shouldGenerateDifferentTokensForDifferentSessions() {
            String token1 = sessionTokenService.generateToken(TEST_CLIENT_ID, "sess_1");
            String token2 = sessionTokenService.generateToken(TEST_CLIENT_ID, "sess_2");
            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("Should generate different tokens for different clients")
        void shouldGenerateDifferentTokensForDifferentClients() {
            String token1 = sessionTokenService.generateToken(1L, TEST_SESSION_ID);
            String token2 = sessionTokenService.generateToken(2L, TEST_SESSION_ID);
            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("Token should contain payload and signature parts")
        void tokenShouldContainTwoParts() {
            String token = sessionTokenService.generateToken(TEST_CLIENT_ID, TEST_SESSION_ID);
            String[] parts = token.split("\\.");
            assertEquals(2, parts.length, "Token should have payload and signature separated by dot");
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should validate a freshly generated token")
        void shouldValidateFreshToken() {
            String token = sessionTokenService.generateToken(TEST_CLIENT_ID, TEST_SESSION_ID);

            SessionTokenService.TokenValidationResult result = sessionTokenService.validateToken(token, TEST_CLIENT_ID);

            assertTrue(result.isValid());
            assertFalse(result.isExpired());
            assertEquals(TEST_SESSION_ID, result.getSessionId());
            assertEquals(TEST_CLIENT_ID, result.getClientId());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject null token")
        void shouldRejectNullToken() {
            SessionTokenService.TokenValidationResult result = sessionTokenService.validateToken(null, TEST_CLIENT_ID);

            assertFalse(result.isValid());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject blank token")
        void shouldRejectBlankToken() {
            SessionTokenService.TokenValidationResult result = sessionTokenService.validateToken("   ", TEST_CLIENT_ID);

            assertFalse(result.isValid());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject token with wrong client ID")
        void shouldRejectTokenWithWrongClientId() {
            String token = sessionTokenService.generateToken(TEST_CLIENT_ID, TEST_SESSION_ID);

            SessionTokenService.TokenValidationResult result = sessionTokenService.validateToken(token, 999L);

            assertFalse(result.isValid());
            assertEquals("Client ID mismatch", result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject malformed token")
        void shouldRejectMalformedToken() {
            SessionTokenService.TokenValidationResult result = sessionTokenService.validateToken("not-a-valid-token", TEST_CLIENT_ID);

            assertFalse(result.isValid());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject token with invalid signature")
        void shouldRejectTokenWithInvalidSignature() {
            String token = sessionTokenService.generateToken(TEST_CLIENT_ID, TEST_SESSION_ID);
            // Corrupt the signature
            String corruptedToken = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature123456";

            SessionTokenService.TokenValidationResult result = sessionTokenService.validateToken(corruptedToken, TEST_CLIENT_ID);

            assertFalse(result.isValid());
            assertEquals("Invalid signature", result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Token Expiry Tests")
    class TokenExpiryTests {

        @Test
        @DisplayName("Should mark token as expired after validity period")
        void shouldExpireTokenAfterValidityPeriod() throws InterruptedException {
            // Set very short validity for testing
            ReflectionTestUtils.setField(sessionTokenService, "tokenValiditySeconds", 1L);

            String token = sessionTokenService.generateToken(TEST_CLIENT_ID, TEST_SESSION_ID);

            // Wait for token to expire
            Thread.sleep(1500);

            SessionTokenService.TokenValidationResult result = sessionTokenService.validateToken(token, TEST_CLIENT_ID);

            assertFalse(result.isValid());
            assertTrue(result.isExpired());
            assertEquals("Token expired", result.getErrorMessage());
            // Session ID should still be extractable from expired token
            assertEquals(TEST_SESSION_ID, result.getSessionId());
        }

        @Test
        @DisplayName("Should accept token within validity period")
        void shouldAcceptTokenWithinValidityPeriod() {
            // Set longer validity for testing
            ReflectionTestUtils.setField(sessionTokenService, "tokenValiditySeconds", 3600L);

            String token = sessionTokenService.generateToken(TEST_CLIENT_ID, TEST_SESSION_ID);

            SessionTokenService.TokenValidationResult result = sessionTokenService.validateToken(token, TEST_CLIENT_ID);

            assertTrue(result.isValid());
            assertFalse(result.isExpired());
        }
    }
}
