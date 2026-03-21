package com.teia.users.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.teia.users.models.*;
import com.teia.users.models.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-for-jwt-authentication-must-be-at-least-32-chars";
    private static final long ACCESS_EXPIRY_MS = 900000;  // 15 minutes
    private static final long REFRESH_EXPIRY_MS = 604800000;  // 7 days

    private JwtService jwtService;
    private TeiaUser testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, ACCESS_EXPIRY_MS, REFRESH_EXPIRY_MS);
        testUser = createTestUser();
    }

    private TeiaUser createTestUser() {
        TeiaUser user = new TeiaUser();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPassword("hashedPassword");
        user.setRole(Role.CLIENT);
        user.setPhoneNumber("+1234567890");

        // Create required nested entities
        ElevenLabsInfo elevenLabsInfo = new ElevenLabsInfo();
        elevenLabsInfo.setId(1L);
        user.setElevenLabsInfo(elevenLabsInfo);

        TwilioInfo twilioInfo = new TwilioInfo();
        twilioInfo.setId(1L);
        user.setTwilioInfo(twilioInfo);

        OpenAIInfo openAIInfo = new OpenAIInfo();
        openAIInfo.setId(1L);
        user.setOpenAIInfo(openAIInfo);

        CalInfo calInfo = new CalInfo();
        calInfo.setId(1L);
        user.setCalInfo(calInfo);

        return user;
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessToken {

        @Test
        @DisplayName("should generate a valid access token")
        void shouldGenerateValidAccessToken() {
            String token = jwtService.generateAccessToken(testUser);

            assertNotNull(token);
            assertFalse(token.isEmpty());
            assertTrue(jwtService.isTokenValid(token));
        }

        @Test
        @DisplayName("should include user claims in access token")
        void shouldIncludeUserClaimsInAccessToken() {
            String token = jwtService.generateAccessToken(testUser);

            DecodedJWT decoded = jwtService.validateToken(token);

            assertNotNull(decoded);
            assertEquals(testUser.getId(), jwtService.extractUserId(decoded));
            assertEquals(testUser.getEmail(), jwtService.extractEmail(decoded));
            assertEquals(testUser.getRole(), jwtService.extractRole(decoded));
        }

        @Test
        @DisplayName("should generate different tokens for different users")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            TeiaUser anotherUser = createTestUser();
            anotherUser.setId(2L);
            anotherUser.setEmail("another@example.com");

            String token1 = jwtService.generateAccessToken(testUser);
            String token2 = jwtService.generateAccessToken(anotherUser);

            assertNotEquals(token1, token2);
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test
        @DisplayName("should generate a valid refresh token")
        void shouldGenerateValidRefreshToken() {
            String token = jwtService.generateRefreshToken(testUser);

            assertNotNull(token);
            assertFalse(token.isEmpty());
            assertTrue(jwtService.isTokenValid(token));
        }

        @Test
        @DisplayName("should include userId in refresh token")
        void shouldIncludeUserIdInRefreshToken() {
            String token = jwtService.generateRefreshToken(testUser);

            Long userId = jwtService.extractUserId(token);

            assertEquals(testUser.getId(), userId);
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("should return decoded JWT for valid token")
        void shouldReturnDecodedJwtForValidToken() {
            String token = jwtService.generateAccessToken(testUser);

            DecodedJWT decoded = jwtService.validateToken(token);

            assertNotNull(decoded);
            assertEquals(testUser.getId().toString(), decoded.getSubject());
        }

        @Test
        @DisplayName("should return null for invalid token")
        void shouldReturnNullForInvalidToken() {
            DecodedJWT decoded = jwtService.validateToken("invalid.token.here");

            assertNull(decoded);
        }

        @Test
        @DisplayName("should return null for tampered token")
        void shouldReturnNullForTamperedToken() {
            String token = jwtService.generateAccessToken(testUser);
            String tamperedToken = token.substring(0, token.length() - 5) + "xxxxx";

            DecodedJWT decoded = jwtService.validateToken(tamperedToken);

            assertNull(decoded);
        }

        @Test
        @DisplayName("should return null for token signed with different secret")
        void shouldReturnNullForTokenSignedWithDifferentSecret() {
            JwtService otherService = new JwtService("different-secret-key-must-be-at-least-32-chars", ACCESS_EXPIRY_MS, REFRESH_EXPIRY_MS);
            String token = otherService.generateAccessToken(testUser);

            DecodedJWT decoded = jwtService.validateToken(token);

            assertNull(decoded);
        }

        @Test
        @DisplayName("should return null for expired token")
        void shouldReturnNullForExpiredToken() {
            // Create service with very short expiry
            JwtService shortExpiryService = new JwtService(TEST_SECRET, 1, REFRESH_EXPIRY_MS);
            String token = shortExpiryService.generateAccessToken(testUser);

            // Wait for token to expire
            try {
                java.lang.Thread.sleep(10);
            } catch (InterruptedException e) {
                java.lang.Thread.currentThread().interrupt();
            }

            DecodedJWT decoded = shortExpiryService.validateToken(token);

            assertNull(decoded);
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("should return true for valid token")
        void shouldReturnTrueForValidToken() {
            String token = jwtService.generateAccessToken(testUser);

            assertTrue(jwtService.isTokenValid(token));
        }

        @Test
        @DisplayName("should return false for invalid token")
        void shouldReturnFalseForInvalidToken() {
            assertFalse(jwtService.isTokenValid("invalid.token"));
        }

        @Test
        @DisplayName("should return false for null token")
        void shouldReturnFalseForNullToken() {
            assertFalse(jwtService.isTokenValid(null));
        }

        @Test
        @DisplayName("should return false for empty token")
        void shouldReturnFalseForEmptyToken() {
            assertFalse(jwtService.isTokenValid(""));
        }
    }

    @Nested
    @DisplayName("extractUserId")
    class ExtractUserId {

        @Test
        @DisplayName("should extract userId from valid token")
        void shouldExtractUserIdFromValidToken() {
            String token = jwtService.generateAccessToken(testUser);

            Long userId = jwtService.extractUserId(token);

            assertEquals(testUser.getId(), userId);
        }

        @Test
        @DisplayName("should return null for invalid token")
        void shouldReturnNullForInvalidToken() {
            Long userId = jwtService.extractUserId("invalid.token");

            assertNull(userId);
        }
    }

    @Nested
    @DisplayName("extractEmail")
    class ExtractEmail {

        @Test
        @DisplayName("should extract email from valid token")
        void shouldExtractEmailFromValidToken() {
            String token = jwtService.generateAccessToken(testUser);

            String email = jwtService.extractEmail(token);

            assertEquals(testUser.getEmail(), email);
        }

        @Test
        @DisplayName("should return null for invalid token")
        void shouldReturnNullForInvalidToken() {
            String email = jwtService.extractEmail("invalid.token");

            assertNull(email);
        }
    }

    @Nested
    @DisplayName("extractRole")
    class ExtractRole {

        @Test
        @DisplayName("should extract role from valid token")
        void shouldExtractRoleFromValidToken() {
            String token = jwtService.generateAccessToken(testUser);

            Role role = jwtService.extractRole(token);

            assertEquals(testUser.getRole(), role);
        }

        @Test
        @DisplayName("should extract ADMIN role correctly")
        void shouldExtractAdminRoleCorrectly() {
            testUser.setRole(Role.ADMIN);
            String token = jwtService.generateAccessToken(testUser);

            Role role = jwtService.extractRole(token);

            assertEquals(Role.ADMIN, role);
        }

        @Test
        @DisplayName("should extract OPERATOR role correctly")
        void shouldExtractOperatorRoleCorrectly() {
            testUser.setRole(Role.OPERATOR);
            String token = jwtService.generateAccessToken(testUser);

            Role role = jwtService.extractRole(token);

            assertEquals(Role.OPERATOR, role);
        }

        @Test
        @DisplayName("should return null for invalid token")
        void shouldReturnNullForInvalidToken() {
            Role role = jwtService.extractRole("invalid.token");

            assertNull(role);
        }
    }

    @Nested
    @DisplayName("getExpiryMethods")
    class GetExpiryMethods {

        @Test
        @DisplayName("should return correct access token expiry")
        void shouldReturnCorrectAccessTokenExpiry() {
            assertEquals(ACCESS_EXPIRY_MS, jwtService.getAccessTokenExpiryMs());
        }

        @Test
        @DisplayName("should return correct refresh token expiry")
        void shouldReturnCorrectRefreshTokenExpiry() {
            assertEquals(REFRESH_EXPIRY_MS, jwtService.getRefreshTokenExpiryMs());
        }

        @Test
        @DisplayName("should return future instant for refresh token expiry")
        void shouldReturnFutureInstantForRefreshTokenExpiry() {
            var expiry = jwtService.getRefreshTokenExpiry();

            assertTrue(expiry.isAfter(java.time.Instant.now()));
        }
    }
}
