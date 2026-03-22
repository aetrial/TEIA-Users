package com.teia.users.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teia.users.dto.CheckoutRequest;
import com.teia.users.models.CreditTransaction;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.enums.CreditBundle;
import com.teia.users.models.enums.CreditTransactionType;
import com.teia.users.models.enums.Role;
import com.teia.users.security.JwtService;
import com.teia.users.services.BillingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("BillingController")
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private BillingService billingService;

    private TeiaUser createTestUser(Long id, String email, Role role) {
        TeiaUser user = new TeiaUser();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setName("Test User");
        user.setPhoneNumber("+1234567890");
        return user;
    }

    private String generateToken(Long userId, String email, Role role) {
        TeiaUser user = createTestUser(userId, email, role);
        return jwtService.generateAccessToken(user);
    }

    @Nested
    @DisplayName("POST /billing/checkout")
    class CheckoutEndpoint {

        @Test
        @DisplayName("should return checkout URL for valid bundle ID with CLIENT role")
        void shouldReturnCheckoutUrlForValidBundleWithClientRole() throws Exception {
            Long clientId = 1L;
            String token = generateToken(clientId, "client@example.com", Role.CLIENT);
            CheckoutRequest request = new CheckoutRequest("BUNDLE_100");
            String expectedUrl = "https://checkout.stripe.com/pay/cs_test_123";

            when(billingService.createCheckoutSession(eq(clientId), eq(CreditBundle.BUNDLE_100)))
                    .thenReturn(expectedUrl);

            mockMvc.perform(post("/billing/checkout")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkoutUrl").value(expectedUrl));

            verify(billingService).createCheckoutSession(eq(clientId), eq(CreditBundle.BUNDLE_100));
        }

        @Test
        @DisplayName("should return checkout URL for BUNDLE_500")
        void shouldReturnCheckoutUrlForBundle500() throws Exception {
            Long clientId = 1L;
            String token = generateToken(clientId, "client@example.com", Role.CLIENT);
            CheckoutRequest request = new CheckoutRequest("BUNDLE_500");
            String expectedUrl = "https://checkout.stripe.com/pay/cs_test_456";

            when(billingService.createCheckoutSession(eq(clientId), eq(CreditBundle.BUNDLE_500)))
                    .thenReturn(expectedUrl);

            mockMvc.perform(post("/billing/checkout")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkoutUrl").value(expectedUrl));
        }

        @Test
        @DisplayName("should return checkout URL for BUNDLE_1000")
        void shouldReturnCheckoutUrlForBundle1000() throws Exception {
            Long clientId = 1L;
            String token = generateToken(clientId, "client@example.com", Role.CLIENT);
            CheckoutRequest request = new CheckoutRequest("BUNDLE_1000");
            String expectedUrl = "https://checkout.stripe.com/pay/cs_test_789";

            when(billingService.createCheckoutSession(eq(clientId), eq(CreditBundle.BUNDLE_1000)))
                    .thenReturn(expectedUrl);

            mockMvc.perform(post("/billing/checkout")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.checkoutUrl").value(expectedUrl));
        }

        @Test
        @DisplayName("should return 400 for invalid bundle ID")
        void shouldReturn400ForInvalidBundleId() throws Exception {
            Long clientId = 1L;
            String token = generateToken(clientId, "client@example.com", Role.CLIENT);
            CheckoutRequest request = new CheckoutRequest("INVALID_BUNDLE");

            mockMvc.perform(post("/billing/checkout")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Invalid bundle ID. Valid values: BUNDLE_100, BUNDLE_500, BUNDLE_1000"));

            verify(billingService, never()).createCheckoutSession(any(), any());
        }

        @Test
        @DisplayName("should return 400 for empty bundle ID")
        void shouldReturn400ForEmptyBundleId() throws Exception {
            Long clientId = 1L;
            String token = generateToken(clientId, "client@example.com", Role.CLIENT);
            CheckoutRequest request = new CheckoutRequest("");

            mockMvc.perform(post("/billing/checkout")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(billingService, never()).createCheckoutSession(any(), any());
        }

        @Test
        @DisplayName("should return 400 for missing bundle ID")
        void shouldReturn400ForMissingBundleId() throws Exception {
            Long clientId = 1L;
            String token = generateToken(clientId, "client@example.com", Role.CLIENT);

            mockMvc.perform(post("/billing/checkout")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verify(billingService, never()).createCheckoutSession(any(), any());
        }

        @Test
        @DisplayName("should return 403 for OPERATOR role")
        void shouldReturn403ForOperatorRole() throws Exception {
            Long userId = 1L;
            String token = generateToken(userId, "operator@example.com", Role.OPERATOR);
            CheckoutRequest request = new CheckoutRequest("BUNDLE_100");

            mockMvc.perform(post("/billing/checkout")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(billingService, never()).createCheckoutSession(any(), any());
        }

        @Test
        @DisplayName("should return 403 for ADMIN role")
        void shouldReturn403ForAdminRole() throws Exception {
            Long userId = 1L;
            String token = generateToken(userId, "admin@example.com", Role.ADMIN);
            CheckoutRequest request = new CheckoutRequest("BUNDLE_100");

            mockMvc.perform(post("/billing/checkout")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(billingService, never()).createCheckoutSession(any(), any());
        }

        @Test
        @DisplayName("should return 401 without authentication")
        void shouldReturn401WithoutAuthentication() throws Exception {
            CheckoutRequest request = new CheckoutRequest("BUNDLE_100");

            mockMvc.perform(post("/billing/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(billingService, never()).createCheckoutSession(any(), any());
        }
    }

    @Nested
    @DisplayName("GET /billing/history")
    class HistoryEndpoint {

        @Test
        @DisplayName("should return transaction history for CLIENT role")
        void shouldReturnTransactionHistoryForClientRole() throws Exception {
            Long clientId = 1L;
            String token = generateToken(clientId, "client@example.com", Role.CLIENT);

            CreditTransaction tx1 = new CreditTransaction(clientId, BigDecimal.valueOf(100), CreditTransactionType.PURCHASE, "stripe_session_1");
            CreditTransaction tx2 = new CreditTransaction(clientId, BigDecimal.valueOf(500), CreditTransactionType.PURCHASE, "stripe_session_2");
            List<CreditTransaction> transactions = List.of(tx2, tx1);

            when(billingService.getTransactionHistory(clientId)).thenReturn(transactions);

            mockMvc.perform(get("/billing/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].amount").value(500))
                    .andExpect(jsonPath("$[0].type").value("PURCHASE"))
                    .andExpect(jsonPath("$[1].amount").value(100));

            verify(billingService).getTransactionHistory(clientId);
        }

        @Test
        @DisplayName("should return empty list when no transactions")
        void shouldReturnEmptyListWhenNoTransactions() throws Exception {
            Long clientId = 1L;
            String token = generateToken(clientId, "client@example.com", Role.CLIENT);

            when(billingService.getTransactionHistory(clientId)).thenReturn(List.of());

            mockMvc.perform(get("/billing/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return 403 for OPERATOR role")
        void shouldReturn403ForOperatorRole() throws Exception {
            Long userId = 1L;
            String token = generateToken(userId, "operator@example.com", Role.OPERATOR);

            mockMvc.perform(get("/billing/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());

            verify(billingService, never()).getTransactionHistory(any());
        }

        @Test
        @DisplayName("should return 403 for ADMIN role")
        void shouldReturn403ForAdminRole() throws Exception {
            Long userId = 1L;
            String token = generateToken(userId, "admin@example.com", Role.ADMIN);

            mockMvc.perform(get("/billing/history")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());

            verify(billingService, never()).getTransactionHistory(any());
        }

        @Test
        @DisplayName("should return 401 without authentication")
        void shouldReturn401WithoutAuthentication() throws Exception {
            mockMvc.perform(get("/billing/history"))
                    .andExpect(status().isUnauthorized());

            verify(billingService, never()).getTransactionHistory(any());
        }
    }
}
