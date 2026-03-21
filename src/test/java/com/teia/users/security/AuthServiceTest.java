package com.teia.users.security;

import com.teia.users.dto.LoginRequest;
import com.teia.users.dto.LoginResponse;
import com.teia.users.dto.RefreshRequest;
import com.teia.users.dto.RefreshResponse;
import com.teia.users.models.*;
import com.teia.users.models.enums.Role;
import com.teia.users.repositories.RefreshTokenRepository;
import com.teia.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;
    private TeiaUser testUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, jwtService, passwordEncoder);
        testUser = createTestUser();
    }

    private TeiaUser createTestUser() {
        TeiaUser user = new TeiaUser();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setPassword("$2a$10$encodedPassword");
        user.setRole(Role.CLIENT);
        user.setPhoneNumber("+1234567890");

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
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return tokens on successful login")
        void shouldReturnTokensOnSuccessfulLogin() {
            LoginRequest request = new LoginRequest("test@example.com", "password123");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);
            when(jwtService.generateAccessToken(testUser)).thenReturn("access-token");
            when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh-token");
            when(jwtService.getRefreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(604800));
            when(jwtService.getAccessTokenExpiryMs()).thenReturn(900000L);

            LoginResponse response = authService.login(request);

            assertNotNull(response);
            assertEquals("access-token", response.getAccessToken());
            assertEquals("refresh-token", response.getRefreshToken());
            assertEquals(900, response.getExpiresIn());
            assertEquals("Bearer", response.getTokenType());

            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("should save refresh token to database on login")
        void shouldSaveRefreshTokenToDatabaseOnLogin() {
            LoginRequest request = new LoginRequest("test@example.com", "password123");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);
            when(jwtService.generateAccessToken(testUser)).thenReturn("access-token");
            when(jwtService.generateRefreshToken(testUser)).thenReturn("refresh-token");
            when(jwtService.getRefreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(604800));
            when(jwtService.getAccessTokenExpiryMs()).thenReturn(900000L);

            authService.login(request);

            ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(tokenCaptor.capture());

            RefreshToken savedToken = tokenCaptor.getValue();
            assertEquals("refresh-token", savedToken.getToken());
            assertEquals(testUser.getId(), savedToken.getUserId());
            assertFalse(savedToken.isRevoked());
        }

        @Test
        @DisplayName("should throw BadCredentialsException for non-existent user")
        void shouldThrowBadCredentialsExceptionForNonExistentUser() {
            LoginRequest request = new LoginRequest("nonexistent@example.com", "password123");

            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            assertThrows(BadCredentialsException.class, () -> authService.login(request));

            verify(passwordEncoder, never()).matches(any(), any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw BadCredentialsException for wrong password")
        void shouldThrowBadCredentialsExceptionForWrongPassword() {
            LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);

            assertThrows(BadCredentialsException.class, () -> authService.login(request));

            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw BadCredentialsException when user has null password")
        void shouldThrowBadCredentialsExceptionWhenUserHasNullPassword() {
            testUser.setPassword(null);
            LoginRequest request = new LoginRequest("test@example.com", "password123");

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            assertThrows(BadCredentialsException.class, () -> authService.login(request));

            verify(passwordEncoder, never()).matches(any(), any());
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("should return new access token on valid refresh")
        void shouldReturnNewAccessTokenOnValidRefresh() {
            RefreshRequest request = new RefreshRequest("valid-refresh-token");
            RefreshToken storedToken = new RefreshToken("valid-refresh-token", testUser.getId(), Instant.now().plusSeconds(604800));

            when(jwtService.validateToken("valid-refresh-token")).thenReturn(mock(com.auth0.jwt.interfaces.DecodedJWT.class));
            when(refreshTokenRepository.findByToken("valid-refresh-token")).thenReturn(Optional.of(storedToken));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(testUser)).thenReturn("new-access-token");
            when(jwtService.getAccessTokenExpiryMs()).thenReturn(900000L);

            RefreshResponse response = authService.refresh(request);

            assertNotNull(response);
            assertEquals("new-access-token", response.getAccessToken());
            assertEquals(900, response.getExpiresIn());
            assertEquals("Bearer", response.getTokenType());
        }

        @Test
        @DisplayName("should throw BadCredentialsException for invalid JWT")
        void shouldThrowBadCredentialsExceptionForInvalidJwt() {
            RefreshRequest request = new RefreshRequest("invalid-token");

            when(jwtService.validateToken("invalid-token")).thenReturn(null);

            assertThrows(BadCredentialsException.class, () -> authService.refresh(request));

            verify(refreshTokenRepository, never()).findByToken(any());
        }

        @Test
        @DisplayName("should throw BadCredentialsException when token not in database")
        void shouldThrowBadCredentialsExceptionWhenTokenNotInDatabase() {
            RefreshRequest request = new RefreshRequest("unknown-token");

            when(jwtService.validateToken("unknown-token")).thenReturn(mock(com.auth0.jwt.interfaces.DecodedJWT.class));
            when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

            assertThrows(BadCredentialsException.class, () -> authService.refresh(request));
        }

        @Test
        @DisplayName("should throw BadCredentialsException for revoked token")
        void shouldThrowBadCredentialsExceptionForRevokedToken() {
            RefreshRequest request = new RefreshRequest("revoked-token");
            RefreshToken storedToken = new RefreshToken("revoked-token", testUser.getId(), Instant.now().plusSeconds(604800));
            storedToken.setRevoked(true);

            when(jwtService.validateToken("revoked-token")).thenReturn(mock(com.auth0.jwt.interfaces.DecodedJWT.class));
            when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(storedToken));

            assertThrows(BadCredentialsException.class, () -> authService.refresh(request));
        }

        @Test
        @DisplayName("should throw BadCredentialsException for expired token in database")
        void shouldThrowBadCredentialsExceptionForExpiredTokenInDatabase() {
            RefreshRequest request = new RefreshRequest("expired-token");
            RefreshToken storedToken = new RefreshToken("expired-token", testUser.getId(), Instant.now().minusSeconds(1));

            when(jwtService.validateToken("expired-token")).thenReturn(mock(com.auth0.jwt.interfaces.DecodedJWT.class));
            when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(storedToken));

            assertThrows(BadCredentialsException.class, () -> authService.refresh(request));
        }

        @Test
        @DisplayName("should throw BadCredentialsException when user not found")
        void shouldThrowBadCredentialsExceptionWhenUserNotFound() {
            RefreshRequest request = new RefreshRequest("valid-token");
            RefreshToken storedToken = new RefreshToken("valid-token", 999L, Instant.now().plusSeconds(604800));

            when(jwtService.validateToken("valid-token")).thenReturn(mock(com.auth0.jwt.interfaces.DecodedJWT.class));
            when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(storedToken));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(BadCredentialsException.class, () -> authService.refresh(request));
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("should revoke refresh token on logout")
        void shouldRevokeRefreshTokenOnLogout() {
            RefreshRequest request = new RefreshRequest("token-to-revoke");
            RefreshToken storedToken = new RefreshToken("token-to-revoke", testUser.getId(), Instant.now().plusSeconds(604800));

            when(refreshTokenRepository.findByToken("token-to-revoke")).thenReturn(Optional.of(storedToken));

            authService.logout(request);

            assertTrue(storedToken.isRevoked());
            verify(refreshTokenRepository).save(storedToken);
        }

        @Test
        @DisplayName("should do nothing when token not found")
        void shouldDoNothingWhenTokenNotFound() {
            RefreshRequest request = new RefreshRequest("unknown-token");

            when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

            authService.logout(request);

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeAllUserTokens")
    class RevokeAllUserTokens {

        @Test
        @DisplayName("should call repository to revoke all user tokens")
        void shouldCallRepositoryToRevokeAllUserTokens() {
            authService.revokeAllUserTokens(testUser.getId());

            verify(refreshTokenRepository).revokeAllByUserId(testUser.getId());
        }
    }
}
