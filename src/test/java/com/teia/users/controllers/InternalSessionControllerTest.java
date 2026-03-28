package com.teia.users.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teia.users.dto.UsageReportRequest;
import com.teia.users.dto.SessionCompleteRequest;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.UsageEvent;
import com.teia.users.models.enums.Role;
import com.teia.users.models.enums.UsageEventStatus;
import com.teia.users.models.enums.UsageEventType;
import com.teia.users.repositories.UserRepository;
import com.teia.users.services.TwilioWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for InternalSessionController.
 * Tests credit reconciliation for completed sessions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TwilioWebhookService webhookService;

    @MockBean
    private UserRepository userRepository;

    @Value("${internal.secret}")
    private String internalSecret;

    private TeiaUser testClient;

    @BeforeEach
    void setUp() {
        testClient = new TeiaUser();
        testClient.setId(1L);
        testClient.setName("Test Client");
        testClient.setEmail("test@example.com");
        testClient.setRole(Role.CLIENT);
        testClient.setCreditBalance(new BigDecimal("95.00")); // Started with 100, reserved 5
    }

    /**
     * Computes HMAC-SHA256 signature for internal authentication.
     */
    private String computeSignature(String body, String timestamp) throws Exception {
        String signatureInput = body + timestamp;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(internalSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder(hmacBytes.length * 2);
        for (byte b : hmacBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    @Nested
    @DisplayName("Session Completion Tests")
    class SessionCompletionTests {

        @Test
        @DisplayName("Should refund credits when actual usage is less than reserved")
        void shouldRefundCreditsForShortCall() throws Exception {
            // Given: Session with 5 credits reserved, actual usage 2 minutes = 2 credits
            UsageEvent usageEvent = new UsageEvent(1L, UsageEventType.CALL, "sess_short123", new BigDecimal("5.00"));
            usageEvent.complete(120, new BigDecimal("2.00")); // 2 minutes = 2 credits

            when(webhookService.reconcileSession(eq("sess_short123"), eq(120), eq(UsageEventStatus.COMPLETED),
                    isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(usageEvent);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            SessionCompleteRequest request = new SessionCompleteRequest();
            request.setClientId(1L);
            request.setSessionId("sess_short123");
            request.setType("CALL");
            request.setActualDurationSeconds(120);
            request.setStatus("COMPLETED");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Session complete callback is received
            mockMvc.perform(post("/internal/session/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: Session is reconciled successfully
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value("sess_short123"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.creditsReserved").value(5.00))
                    .andExpect(jsonPath("$.creditsUsed").value(2.00))
                    .andExpect(jsonPath("$.creditsDifference").value(3.00)); // 5 - 2 = 3 refunded

            verify(webhookService).reconcileSession("sess_short123", 120, UsageEventStatus.COMPLETED, null, null, null, null, null);
        }

        @Test
        @DisplayName("Should deduct additional credits when actual usage exceeds reserved")
        void shouldDeductCreditsForLongCall() throws Exception {
            // Given: Session with 5 credits reserved, actual usage 10 minutes = 10 credits
            UsageEvent usageEvent = new UsageEvent(1L, UsageEventType.CALL, "sess_long123", new BigDecimal("5.00"));
            usageEvent.complete(600, new BigDecimal("10.00")); // 10 minutes = 10 credits

            when(webhookService.reconcileSession(eq("sess_long123"), eq(600), eq(UsageEventStatus.COMPLETED),
                    isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(usageEvent);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            SessionCompleteRequest request = new SessionCompleteRequest();
            request.setClientId(1L);
            request.setSessionId("sess_long123");
            request.setType("CALL");
            request.setActualDurationSeconds(600);
            request.setStatus("COMPLETED");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Session complete callback is received
            mockMvc.perform(post("/internal/session/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: Session is reconciled with additional charge
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creditsReserved").value(5.00))
                    .andExpect(jsonPath("$.creditsUsed").value(10.00))
                    .andExpect(jsonPath("$.creditsDifference").value(-5.00)); // 5 - 10 = -5 additional charge

            verify(webhookService).reconcileSession("sess_long123", 600, UsageEventStatus.COMPLETED, null, null, null, null, null);
        }

        @Test
        @DisplayName("Should refund all credits for failed session")
        void shouldRefundAllCreditsForFailedSession() throws Exception {
            // Given: Session that failed
            UsageEvent usageEvent = new UsageEvent(1L, UsageEventType.CALL, "sess_failed123", new BigDecimal("5.00"));
            usageEvent.fail();
            usageEvent.setCreditsUsed(BigDecimal.ZERO);

            when(webhookService.reconcileSession(eq("sess_failed123"), isNull(), eq(UsageEventStatus.FAILED),
                    isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(usageEvent);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            SessionCompleteRequest request = new SessionCompleteRequest();
            request.setClientId(1L);
            request.setSessionId("sess_failed123");
            request.setType("CALL");
            request.setActualDurationSeconds(null);
            request.setStatus("FAILED");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Session complete callback is received with FAILED status
            mockMvc.perform(post("/internal/session/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: All credits are refunded
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.creditsUsed").value(0))
                    .andExpect(jsonPath("$.creditsDifference").value(5.00)); // Full refund

            verify(webhookService).reconcileSession("sess_failed123", null, UsageEventStatus.FAILED, null, null, null, null, null);
        }

        @Test
        @DisplayName("Should return 404 for unknown session")
        void shouldReturn404ForUnknownSession() throws Exception {
            // Given: Session not found
            when(webhookService.reconcileSession(eq("sess_unknown"), any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new TwilioWebhookService.SessionNotFoundException("Session not found: sess_unknown"));

            SessionCompleteRequest request = new SessionCompleteRequest();
            request.setClientId(1L);
            request.setSessionId("sess_unknown");
            request.setType("CALL");
            request.setActualDurationSeconds(60);
            request.setStatus("COMPLETED");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Session complete callback is received
            mockMvc.perform(post("/internal/session/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: 404 is returned
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Session not found"));
        }
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should reject request without authentication headers")
        void shouldRejectUnauthenticatedRequest() throws Exception {
            SessionCompleteRequest request = new SessionCompleteRequest();
            request.setClientId(1L);
            request.setSessionId("sess_test");
            request.setType("CALL");
            request.setActualDurationSeconds(60);
            request.setStatus("COMPLETED");

            // When: Request is sent without auth headers
            mockMvc.perform(post("/internal/session/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    // Then: 401 is returned
                    .andExpect(status().isUnauthorized());

            verify(webhookService, never()).reconcileSession(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should reject request with invalid signature")
        void shouldRejectInvalidSignature() throws Exception {
            SessionCompleteRequest request = new SessionCompleteRequest();
            request.setClientId(1L);
            request.setSessionId("sess_test");
            request.setType("CALL");
            request.setActualDurationSeconds(60);
            request.setStatus("COMPLETED");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());

            // When: Request is sent with invalid signature
            mockMvc.perform(post("/internal/session/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", "invalid_signature")
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: 401 is returned
                    .andExpect(status().isUnauthorized());

            verify(webhookService, never()).reconcileSession(any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should reject request with expired timestamp")
        void shouldRejectExpiredTimestamp() throws Exception {
            SessionCompleteRequest request = new SessionCompleteRequest();
            request.setClientId(1L);
            request.setSessionId("sess_test");
            request.setType("CALL");
            request.setActualDurationSeconds(60);
            request.setStatus("COMPLETED");

            String body = objectMapper.writeValueAsString(request);
            // Timestamp from 5 minutes ago (expired)
            String timestamp = String.valueOf(System.currentTimeMillis() - 300000);
            String signature = computeSignature(body, timestamp);

            // When: Request is sent with expired timestamp
            mockMvc.perform(post("/internal/session/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: 401 is returned
                    .andExpect(status().isUnauthorized());

            verify(webhookService, never()).reconcileSession(any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Usage Report Tests")
    class UsageReportTests {

        @Test
        @DisplayName("Should deduct credits for a 3-minute call")
        void shouldDeductCreditsForCall() throws Exception {
            // Given: Client with 50 credits, 3 minute call = 3 credits
            testClient.setCreditBalance(new BigDecimal("50.00"));
            UsageEvent usageEvent = new UsageEvent(1L, UsageEventType.CALL, "sess_usage123", new BigDecimal("3.00"));
            usageEvent.complete(180, new BigDecimal("3.00"));

            when(webhookService.reportUsage(eq(1L), eq(UsageEventType.CALL), eq(180), eq("sess_usage123")))
                    .thenReturn(usageEvent);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            UsageReportRequest request = new UsageReportRequest();
            request.setClientId(1L);
            request.setType("CALL");
            request.setDurationSeconds(180);
            request.setSessionId("sess_usage123");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Usage report is received
            mockMvc.perform(post("/internal/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: Usage is recorded
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value("sess_usage123"))
                    .andExpect(jsonPath("$.creditsUsed").value(3.00));

            verify(webhookService).reportUsage(1L, UsageEventType.CALL, 180, "sess_usage123");
        }

        @Test
        @DisplayName("Should deduct 1 credit for a message")
        void shouldDeductCreditsForMessage() throws Exception {
            // Given: Client with 50 credits, message = 1 credit
            testClient.setCreditBalance(new BigDecimal("50.00"));
            UsageEvent usageEvent = new UsageEvent(1L, UsageEventType.MESSAGE, "sess_msg123", new BigDecimal("1.00"));
            usageEvent.complete(null, new BigDecimal("1.00"));

            when(webhookService.reportUsage(eq(1L), eq(UsageEventType.MESSAGE), isNull(), eq("sess_msg123")))
                    .thenReturn(usageEvent);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            UsageReportRequest request = new UsageReportRequest();
            request.setClientId(1L);
            request.setType("MESSAGE");
            request.setDurationSeconds(null);
            request.setSessionId("sess_msg123");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Usage report is received
            mockMvc.perform(post("/internal/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: Usage is recorded
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creditsUsed").value(1.00));

            verify(webhookService).reportUsage(1L, UsageEventType.MESSAGE, null, "sess_msg123");
        }

        @Test
        @DisplayName("Should return 402 when client has insufficient credits")
        void shouldReturn402ForInsufficientCredits() throws Exception {
            // Given: Client with 0 credits
            when(webhookService.reportUsage(eq(1L), eq(UsageEventType.CALL), eq(60), eq("sess_nofunds")))
                    .thenThrow(new TwilioWebhookService.InsufficientCreditsException("Insufficient credits. Balance: 0.00, Required: 1.00"));

            UsageReportRequest request = new UsageReportRequest();
            request.setClientId(1L);
            request.setType("CALL");
            request.setDurationSeconds(60);
            request.setSessionId("sess_nofunds");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Usage report is received
            mockMvc.perform(post("/internal/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: 402 is returned
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.error").value("Insufficient credits"));
        }

        @Test
        @DisplayName("Should return 404 when client is not found")
        void shouldReturn404ForUnknownClient() throws Exception {
            // Given: Client not found
            when(webhookService.reportUsage(eq(999L), any(), any(), any()))
                    .thenThrow(new TwilioWebhookService.ClientNotFoundException("Client not found: 999"));

            UsageReportRequest request = new UsageReportRequest();
            request.setClientId(999L);
            request.setType("CALL");
            request.setDurationSeconds(60);
            request.setSessionId("sess_unknown_client");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Usage report is received
            mockMvc.perform(post("/internal/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: 404 is returned
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Client not found"));
        }

        @Test
        @DisplayName("Should return 400 for invalid type")
        void shouldReturn400ForInvalidType() throws Exception {
            UsageReportRequest request = new UsageReportRequest();
            request.setClientId(1L);
            request.setType("INVALID_TYPE");
            request.setDurationSeconds(60);
            request.setSessionId("sess_bad_type");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            // When: Usage report with invalid type
            mockMvc.perform(post("/internal/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    // Then: 400 is returned
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid type. Must be CALL or MESSAGE"));

            verify(webhookService, never()).reportUsage(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should deduct 2 credits for 61-second call (rounds up)")
        void shouldRoundUpCallDuration() throws Exception {
            // Given: 61 second call should round up to 2 minutes = 2 credits
            testClient.setCreditBalance(new BigDecimal("50.00"));
            UsageEvent usageEvent = new UsageEvent(1L, UsageEventType.CALL, "sess_61sec", new BigDecimal("2.00"));
            usageEvent.complete(61, new BigDecimal("2.00"));

            when(webhookService.reportUsage(eq(1L), eq(UsageEventType.CALL), eq(61), eq("sess_61sec")))
                    .thenReturn(usageEvent);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            UsageReportRequest request = new UsageReportRequest();
            request.setClientId(1L);
            request.setType("CALL");
            request.setDurationSeconds(61);
            request.setSessionId("sess_61sec");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            mockMvc.perform(post("/internal/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creditsUsed").value(2.00));
        }

        @Test
        @DisplayName("Should deduct 1 credit for exactly 60-second call")
        void shouldDeductOneCreditsFor60Seconds() throws Exception {
            // Given: 60 second call = 1 minute = 1 credit
            testClient.setCreditBalance(new BigDecimal("50.00"));
            UsageEvent usageEvent = new UsageEvent(1L, UsageEventType.CALL, "sess_60sec", new BigDecimal("1.00"));
            usageEvent.complete(60, new BigDecimal("1.00"));

            when(webhookService.reportUsage(eq(1L), eq(UsageEventType.CALL), eq(60), eq("sess_60sec")))
                    .thenReturn(usageEvent);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            UsageReportRequest request = new UsageReportRequest();
            request.setClientId(1L);
            request.setType("CALL");
            request.setDurationSeconds(60);
            request.setSessionId("sess_60sec");

            String body = objectMapper.writeValueAsString(request);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature(body, timestamp);

            mockMvc.perform(post("/internal/usage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1")
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creditsUsed").value(1.00));
        }
    }

    @Nested
    @DisplayName("Credit Check Tests")
    class CreditCheckTests {

        @Test
        @DisplayName("Should return credit balance and hasCredits=true when balance >= 1")
        void shouldReturnCreditsWithHasCreditsTrue() throws Exception {
            // Given: Client with 50 credits
            testClient.setCreditBalance(new BigDecimal("50.00"));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature("", timestamp);

            // When: Credit check is requested
            mockMvc.perform(get("/internal/clients/1/credits")
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1"))
                    // Then: Credits are returned
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clientId").value(1))
                    .andExpect(jsonPath("$.creditBalance").value(50.00))
                    .andExpect(jsonPath("$.hasCredits").value(true));
        }

        @Test
        @DisplayName("Should return hasCredits=false when balance < 1")
        void shouldReturnHasCreditsFlaseWhenBalanceLow() throws Exception {
            // Given: Client with 0.50 credits
            testClient.setCreditBalance(new BigDecimal("0.50"));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testClient));

            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature("", timestamp);

            // When: Credit check is requested
            mockMvc.perform(get("/internal/clients/1/credits")
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1"))
                    // Then: hasCredits is false
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasCredits").value(false));
        }

        @Test
        @DisplayName("Should return 404 when client is not found")
        void shouldReturn404ForUnknownClient() throws Exception {
            // Given: Client not found
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = computeSignature("", timestamp);

            // When: Credit check is requested
            mockMvc.perform(get("/internal/clients/999/credits")
                    .header("X-Internal-Key", signature)
                    .header("X-Timestamp", timestamp)
                    .header("X-Client-ID", "1"))
                    // Then: 404 is returned
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Client not found"));
        }
    }
}
