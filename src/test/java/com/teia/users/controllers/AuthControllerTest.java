package com.teia.users.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teia.users.dto.LoginRequest;
import com.teia.users.dto.LoginResponse;
import com.teia.users.dto.RefreshRequest;
import com.teia.users.dto.RefreshResponse;
import com.teia.users.security.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Nested
    @DisplayName("POST /auth/login")
    class LoginEndpoint {

        @Test
        @DisplayName("should return 200 and tokens on successful login")
        void shouldReturn200AndTokensOnSuccessfulLogin() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            LoginResponse response = new LoginResponse("access-token", "refresh-token", 900);

            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900));

            verify(authService).login(any(LoginRequest.class));
        }

        @Test
        @DisplayName("should return 401 on invalid credentials")
        void shouldReturn401OnInvalidCredentials() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BadCredentialsException("Invalid email or password"));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("should return 400 for missing email")
        void shouldReturn400ForMissingEmail() throws Exception {
            String requestJson = "{\"password\":\"password123\"}";

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("should return 400 for missing password")
        void shouldReturn400ForMissingPassword() throws Exception {
            String requestJson = "{\"email\":\"test@example.com\"}";

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("should return 400 for invalid email format")
        void shouldReturn400ForInvalidEmailFormat() throws Exception {
            String requestJson = "{\"email\":\"invalid-email\",\"password\":\"password123\"}";

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));

            verify(authService, never()).login(any());
        }

        @Test
        @DisplayName("should return 400 for empty request body")
        void shouldReturn400ForEmptyRequestBody() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).login(any());
        }
    }

    @Nested
    @DisplayName("POST /auth/refresh")
    class RefreshEndpoint {

        @Test
        @DisplayName("should return 200 and new access token on successful refresh")
        void shouldReturn200AndNewAccessTokenOnSuccessfulRefresh() throws Exception {
            RefreshRequest request = new RefreshRequest("valid-refresh-token");
            RefreshResponse response = new RefreshResponse("new-access-token", 900);

            when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900));

            verify(authService).refresh(any(RefreshRequest.class));
        }

        @Test
        @DisplayName("should return 401 for invalid refresh token")
        void shouldReturn401ForInvalidRefreshToken() throws Exception {
            RefreshRequest request = new RefreshRequest("invalid-token");

            when(authService.refresh(any(RefreshRequest.class)))
                    .thenThrow(new BadCredentialsException("Invalid or expired refresh token"));

            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        @DisplayName("should return 401 for revoked refresh token")
        void shouldReturn401ForRevokedRefreshToken() throws Exception {
            RefreshRequest request = new RefreshRequest("revoked-token");

            when(authService.refresh(any(RefreshRequest.class)))
                    .thenThrow(new BadCredentialsException("Refresh token has been revoked or expired"));

            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 for missing refresh token")
        void shouldReturn400ForMissingRefreshToken() throws Exception {
            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).refresh(any());
        }

        @Test
        @DisplayName("should return 400 for blank refresh token")
        void shouldReturn400ForBlankRefreshToken() throws Exception {
            String requestJson = "{\"refreshToken\":\"\"}";

            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).refresh(any());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout")
    class LogoutEndpoint {

        @Test
        @DisplayName("should return 200 on successful logout")
        void shouldReturn200OnSuccessfulLogout() throws Exception {
            RefreshRequest request = new RefreshRequest("token-to-revoke");

            doNothing().when(authService).logout(any(RefreshRequest.class));

            mockMvc.perform(post("/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).logout(any(RefreshRequest.class));
        }

        @Test
        @DisplayName("should return 200 even for unknown token")
        void shouldReturn200EvenForUnknownToken() throws Exception {
            RefreshRequest request = new RefreshRequest("unknown-token");

            doNothing().when(authService).logout(any(RefreshRequest.class));

            mockMvc.perform(post("/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 400 for missing refresh token")
        void shouldReturn400ForMissingRefreshToken() throws Exception {
            mockMvc.perform(post("/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).logout(any());
        }
    }

    @Nested
    @DisplayName("Authentication not required")
    class AuthenticationNotRequired {

        @Test
        @DisplayName("login endpoint should be accessible without authentication")
        void loginEndpointShouldBeAccessibleWithoutAuthentication() throws Exception {
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            LoginResponse response = new LoginResponse("access-token", "refresh-token", 900);

            when(authService.login(any())).thenReturn(response);

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("refresh endpoint should be accessible without authentication")
        void refreshEndpointShouldBeAccessibleWithoutAuthentication() throws Exception {
            RefreshRequest request = new RefreshRequest("refresh-token");
            RefreshResponse response = new RefreshResponse("new-access-token", 900);

            when(authService.refresh(any())).thenReturn(response);

            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("logout endpoint should be accessible without authentication")
        void logoutEndpointShouldBeAccessibleWithoutAuthentication() throws Exception {
            RefreshRequest request = new RefreshRequest("refresh-token");

            doNothing().when(authService).logout(any());

            mockMvc.perform(post("/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }
}
