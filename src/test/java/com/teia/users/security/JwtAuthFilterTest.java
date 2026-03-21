package com.teia.users.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.teia.users.models.enums.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter")
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private DecodedJWT decodedJWT;

    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        jwtAuthFilter = new JwtAuthFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        @DisplayName("should continue filter chain when no Authorization header")
        void shouldContinueFilterChainWhenNoAuthorizationHeader() throws Exception {
            when(request.getHeader("Authorization")).thenReturn(null);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("should continue filter chain when Authorization header is not Bearer")
        void shouldContinueFilterChainWhenAuthorizationHeaderIsNotBearer() throws Exception {
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("should set authentication for valid Bearer token")
        void shouldSetAuthenticationForValidBearerToken() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(decodedJWT);
            when(jwtService.extractUserId(decodedJWT)).thenReturn(1L);
            when(jwtService.extractEmail(decodedJWT)).thenReturn("test@example.com");
            when(jwtService.extractRole(decodedJWT)).thenReturn(Role.CLIENT);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth);
            assertTrue(auth.isAuthenticated());

            JwtUserPrincipal principal = (JwtUserPrincipal) auth.getPrincipal();
            assertEquals(1L, principal.getUserId());
            assertEquals("test@example.com", principal.getEmail());
            assertEquals(Role.CLIENT, principal.getRole());
        }

        @Test
        @DisplayName("should set correct authority for ADMIN role")
        void shouldSetCorrectAuthorityForAdminRole() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(decodedJWT);
            when(jwtService.extractUserId(decodedJWT)).thenReturn(1L);
            when(jwtService.extractEmail(decodedJWT)).thenReturn("admin@example.com");
            when(jwtService.extractRole(decodedJWT)).thenReturn(Role.ADMIN);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertTrue(auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        }

        @Test
        @DisplayName("should set correct authority for OPERATOR role")
        void shouldSetCorrectAuthorityForOperatorRole() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(decodedJWT);
            when(jwtService.extractUserId(decodedJWT)).thenReturn(1L);
            when(jwtService.extractEmail(decodedJWT)).thenReturn("operator@example.com");
            when(jwtService.extractRole(decodedJWT)).thenReturn(Role.OPERATOR);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertTrue(auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR")));
        }

        @Test
        @DisplayName("should set correct authority for CLIENT role")
        void shouldSetCorrectAuthorityForClientRole() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(decodedJWT);
            when(jwtService.extractUserId(decodedJWT)).thenReturn(1L);
            when(jwtService.extractEmail(decodedJWT)).thenReturn("client@example.com");
            when(jwtService.extractRole(decodedJWT)).thenReturn(Role.CLIENT);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertTrue(auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT")));
        }

        @Test
        @DisplayName("should not set authentication for invalid token")
        void shouldNotSetAuthenticationForInvalidToken() throws Exception {
            String token = "invalid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(null);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("should not set authentication when userId is null")
        void shouldNotSetAuthenticationWhenUserIdIsNull() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(decodedJWT);
            when(jwtService.extractUserId(decodedJWT)).thenReturn(null);
            when(jwtService.extractEmail(decodedJWT)).thenReturn("test@example.com");
            when(jwtService.extractRole(decodedJWT)).thenReturn(Role.CLIENT);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("should not set authentication when email is null")
        void shouldNotSetAuthenticationWhenEmailIsNull() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(decodedJWT);
            when(jwtService.extractUserId(decodedJWT)).thenReturn(1L);
            when(jwtService.extractEmail(decodedJWT)).thenReturn(null);
            when(jwtService.extractRole(decodedJWT)).thenReturn(Role.CLIENT);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("should not set authentication when role is null")
        void shouldNotSetAuthenticationWhenRoleIsNull() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(decodedJWT);
            when(jwtService.extractUserId(decodedJWT)).thenReturn(1L);
            when(jwtService.extractEmail(decodedJWT)).thenReturn("test@example.com");
            when(jwtService.extractRole(decodedJWT)).thenReturn(null);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("should continue filter chain and not set auth when exception occurs")
        void shouldContinueFilterChainAndNotSetAuthWhenExceptionOccurs() throws Exception {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenThrow(new RuntimeException("Unexpected error"));

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }

        @Test
        @DisplayName("should not override existing authentication")
        void shouldNotOverrideExistingAuthentication() throws Exception {
            // Set up existing authentication
            JwtUserPrincipal existingPrincipal = new JwtUserPrincipal(99L, "existing@example.com", Role.ADMIN);
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken existingAuth =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            existingPrincipal, null, java.util.Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(existingAuth);

            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtService.validateToken(token)).thenReturn(decodedJWT);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            JwtUserPrincipal principal = (JwtUserPrincipal) auth.getPrincipal();
            assertEquals(99L, principal.getUserId());
            assertEquals("existing@example.com", principal.getEmail());
        }

        @Test
        @DisplayName("should handle Bearer prefix with extra spaces")
        void shouldHandleBearerPrefixCorrectly() throws Exception {
            // Only "Bearer " with exactly one space should work
            when(request.getHeader("Authorization")).thenReturn("Bearer  extra-space-token");
            when(jwtService.validateToken(" extra-space-token")).thenReturn(null);

            jwtAuthFilter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }
}
