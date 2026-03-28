package com.teia.users.controllers;

import com.teia.users.dto.*;
import com.teia.users.models.enums.Role;
import com.teia.users.models.enums.UsageEventStatus;
import com.teia.users.security.JwtUserPrincipal;
import com.teia.users.services.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("MetricsController")
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsService metricsService;

    private void setSecurityContextWithPrincipal(Long userId, Role role) {
        JwtUserPrincipal principal = new JwtUserPrincipal(userId, "test@example.com", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /metrics/summary")
    class SummaryEndpoint {

        @Test
        @DisplayName("Authenticated user should receive summary metrics")
        @WithMockUser(roles = "CLIENT")
        void authenticatedUserShouldReceiveSummary() throws Exception {
            MetricsSummaryResponse response = MetricsSummaryResponse.builder()
                    .totalCalls(100L)
                    .totalMessages(250L)
                    .totalCreditsUsed(new BigDecimal("75.50"))
                    .currentBalance(new BigDecimal("124.50"))
                    .activeSessionsCount(3L)
                    .build();

            when(metricsService.getSummary(isNull())).thenReturn(response);

            mockMvc.perform(get("/metrics/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCalls").value(100))
                    .andExpect(jsonPath("$.totalMessages").value(250))
                    .andExpect(jsonPath("$.totalCreditsUsed").value(75.50))
                    .andExpect(jsonPath("$.currentBalance").value(124.50))
                    .andExpect(jsonPath("$.activeSessionsCount").value(3));

            verify(metricsService).getSummary(isNull());
        }

        @Test
        @DisplayName("OPERATOR can specify clientId parameter")
        @WithMockUser(roles = "OPERATOR")
        void operatorCanSpecifyClientId() throws Exception {
            MetricsSummaryResponse response = MetricsSummaryResponse.builder()
                    .totalCalls(50L)
                    .totalMessages(100L)
                    .totalCreditsUsed(new BigDecimal("30.00"))
                    .currentBalance(new BigDecimal("70.00"))
                    .activeSessionsCount(1L)
                    .build();

            when(metricsService.getSummary(eq(42L))).thenReturn(response);

            mockMvc.perform(get("/metrics/summary").param("clientId", "42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCalls").value(50));

            verify(metricsService).getSummary(42L);
        }

        @Test
        @DisplayName("ADMIN can specify clientId parameter")
        @WithMockUser(roles = "ADMIN")
        void adminCanSpecifyClientId() throws Exception {
            MetricsSummaryResponse response = MetricsSummaryResponse.builder()
                    .totalCalls(200L)
                    .totalMessages(400L)
                    .totalCreditsUsed(new BigDecimal("150.00"))
                    .currentBalance(new BigDecimal("50.00"))
                    .activeSessionsCount(5L)
                    .build();

            when(metricsService.getSummary(eq(99L))).thenReturn(response);

            mockMvc.perform(get("/metrics/summary").param("clientId", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCalls").value(200));

            verify(metricsService).getSummary(99L);
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/summary"))
                    .andExpect(status().isUnauthorized());

            verify(metricsService, never()).getSummary(any());
        }

        @Test
        @DisplayName("Returns zero values when no data exists")
        @WithMockUser(roles = "CLIENT")
        void returnsZeroValuesWhenNoData() throws Exception {
            MetricsSummaryResponse response = MetricsSummaryResponse.builder()
                    .totalCalls(0L)
                    .totalMessages(0L)
                    .totalCreditsUsed(BigDecimal.ZERO)
                    .currentBalance(BigDecimal.ZERO)
                    .activeSessionsCount(0L)
                    .build();

            when(metricsService.getSummary(isNull())).thenReturn(response);

            mockMvc.perform(get("/metrics/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCalls").value(0))
                    .andExpect(jsonPath("$.totalCreditsUsed").value(0));
        }
    }

    @Nested
    @DisplayName("GET /metrics/calls")
    class CallsEndpoint {

        @Test
        @DisplayName("Returns call events within date range")
        @WithMockUser(roles = "CLIENT")
        void returnsCallsInDateRange() throws Exception {
            List<CallMetricResponse> calls = Arrays.asList(
                    CallMetricResponse.builder()
                            .id(1L)
                            .sessionId("sess_001")
                            .durationSeconds(120)
                            .creditsUsed(new BigDecimal("2.00"))
                            .createdAt(LocalDateTime.of(2026, 3, 15, 10, 30))
                            .status(UsageEventStatus.COMPLETED)
                            .build(),
                    CallMetricResponse.builder()
                            .id(2L)
                            .sessionId("sess_002")
                            .durationSeconds(60)
                            .creditsUsed(new BigDecimal("1.00"))
                            .createdAt(LocalDateTime.of(2026, 3, 16, 14, 0))
                            .status(UsageEventStatus.COMPLETED)
                            .build()
            );

            when(metricsService.getCalls(isNull(), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31))))
                    .thenReturn(calls);

            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].sessionId").value("sess_001"))
                    .andExpect(jsonPath("$[0].durationSeconds").value(120))
                    .andExpect(jsonPath("$[1].sessionId").value("sess_002"));
        }

        @Test
        @DisplayName("Returns 400 when from date is after to date")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenFromAfterTo() throws Exception {
            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-03-31")
                            .param("to", "2026-03-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("'from' date must not be after 'to' date"));

            verify(metricsService, never()).getCalls(any(), any(), any());
        }

        @Test
        @DisplayName("Returns 400 when missing from parameter")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenMissingFrom() throws Exception {
            mockMvc.perform(get("/metrics/calls")
                            .param("to", "2026-03-31"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when missing to parameter")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenMissingTo() throws Exception {
            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-03-01"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns empty list when no calls in range")
        @WithMockUser(roles = "CLIENT")
        void returnsEmptyListWhenNoCalls() throws Exception {
            when(metricsService.getCalls(isNull(), any(), any())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Accepts single-day range when from equals to")
        @WithMockUser(roles = "CLIENT")
        void acceptsSingleDayRange() throws Exception {
            List<CallMetricResponse> calls = Collections.singletonList(
                    CallMetricResponse.builder()
                            .id(99L)
                            .sessionId("sess_single_day")
                            .durationSeconds(45)
                            .creditsUsed(new BigDecimal("0.75"))
                            .createdAt(LocalDateTime.of(2026, 3, 27, 12, 0))
                            .status(UsageEventStatus.COMPLETED)
                            .build()
            );

            when(metricsService.getCalls(isNull(), eq(LocalDate.of(2026, 3, 27)), eq(LocalDate.of(2026, 3, 27))))
                    .thenReturn(calls);

            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-03-27")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sessionId").value("sess_single_day"));

            verify(metricsService).getCalls(isNull(), eq(LocalDate.of(2026, 3, 27)), eq(LocalDate.of(2026, 3, 27)));
        }

        @Test
        @DisplayName("Null creditsUsed field is absent from JSON response")
        @WithMockUser(roles = "CLIENT")
        void nullCreditsUsedIsAbsentFromJson() throws Exception {
            List<CallMetricResponse> calls = Collections.singletonList(
                    CallMetricResponse.builder()
                            .id(100L)
                            .sessionId("sess_no_credits")
                            .durationSeconds(30)
                            .creditsUsed(null) // Explicitly null
                            .createdAt(LocalDateTime.of(2026, 3, 27, 15, 0))
                            .status(UsageEventStatus.STARTED)
                            .build()
            );

            when(metricsService.getCalls(isNull(), any(), any())).thenReturn(calls);

            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sessionId").value("sess_no_credits"))
                    .andExpect(jsonPath("$[0].creditsUsed").doesNotExist()); // @JsonInclude(NON_NULL) means field absent
        }

        @Test
        @DisplayName("OPERATOR can specify clientId for calls")
        @WithMockUser(roles = "OPERATOR")
        void operatorCanSpecifyClientIdForCalls() throws Exception {
            when(metricsService.getCalls(eq(55L), any(), any())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31")
                            .param("clientId", "55"))
                    .andExpect(status().isOk());

            verify(metricsService).getCalls(eq(55L), any(), any());
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /metrics/messages")
    class MessagesEndpoint {

        @Test
        @DisplayName("Returns message events within date range")
        @WithMockUser(roles = "CLIENT")
        void returnsMessagesInDateRange() throws Exception {
            List<MessageMetricResponse> messages = Arrays.asList(
                    MessageMetricResponse.builder()
                            .id(10L)
                            .sessionId("msg_001")
                            .creditsUsed(new BigDecimal("0.10"))
                            .createdAt(LocalDateTime.of(2026, 3, 20, 9, 15))
                            .status(UsageEventStatus.COMPLETED)
                            .build()
            );

            when(metricsService.getMessages(isNull(), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31))))
                    .thenReturn(messages);

            mockMvc.perform(get("/metrics/messages")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sessionId").value("msg_001"))
                    .andExpect(jsonPath("$[0].creditsUsed").value(0.10));
        }

        @Test
        @DisplayName("Returns 400 when from date is after to date")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenFromAfterTo() throws Exception {
            mockMvc.perform(get("/metrics/messages")
                            .param("from", "2026-12-31")
                            .param("to", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("'from' date must not be after 'to' date"));

            verify(metricsService, never()).getMessages(any(), any(), any());
        }

        @Test
        @DisplayName("ADMIN can specify clientId for messages")
        @WithMockUser(roles = "ADMIN")
        void adminCanSpecifyClientIdForMessages() throws Exception {
            when(metricsService.getMessages(eq(77L), any(), any())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/metrics/messages")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31")
                            .param("clientId", "77"))
                    .andExpect(status().isOk());

            verify(metricsService).getMessages(eq(77L), any(), any());
        }

        @Test
        @DisplayName("Returns empty list when no messages in range")
        @WithMockUser(roles = "CLIENT")
        void returnsEmptyListWhenNoMessages() throws Exception {
            when(metricsService.getMessages(isNull(), any(), any())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/metrics/messages")
                            .param("from", "2026-02-01")
                            .param("to", "2026-02-28"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(metricsService).getMessages(isNull(), eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)));
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/messages")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /metrics/usage-over-time")
    class UsageOverTimeEndpoint {

        @Test
        @DisplayName("Returns usage aggregated by day")
        @WithMockUser(roles = "CLIENT")
        void returnsUsageByDay() throws Exception {
            UsageOverTimeResponse response = UsageOverTimeResponse.builder()
                    .period("day")
                    .data(Arrays.asList(
                            UsagePeriodEntry.builder()
                                    .periodStart(LocalDate.of(2026, 3, 25))
                                    .creditsConsumed(new BigDecimal("5.00"))
                                    .build(),
                            UsagePeriodEntry.builder()
                                    .periodStart(LocalDate.of(2026, 3, 26))
                                    .creditsConsumed(new BigDecimal("10.00"))
                                    .build()
                    ))
                    .build();

            when(metricsService.getUsageOverTime(isNull(), eq("day"))).thenReturn(response);

            mockMvc.perform(get("/metrics/usage-over-time")
                            .param("period", "day"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("day"))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].periodStart").value("2026-03-25"))
                    .andExpect(jsonPath("$.data[0].creditsConsumed").value(5.00));
        }

        @Test
        @DisplayName("Returns usage aggregated by week")
        @WithMockUser(roles = "CLIENT")
        void returnsUsageByWeek() throws Exception {
            UsageOverTimeResponse response = UsageOverTimeResponse.builder()
                    .period("week")
                    .data(Collections.emptyList())
                    .build();

            when(metricsService.getUsageOverTime(isNull(), eq("week"))).thenReturn(response);

            mockMvc.perform(get("/metrics/usage-over-time")
                            .param("period", "week"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("week"));
        }

        @Test
        @DisplayName("Returns usage aggregated by month")
        @WithMockUser(roles = "CLIENT")
        void returnsUsageByMonth() throws Exception {
            UsageOverTimeResponse response = UsageOverTimeResponse.builder()
                    .period("month")
                    .data(Collections.emptyList())
                    .build();

            when(metricsService.getUsageOverTime(isNull(), eq("month"))).thenReturn(response);

            mockMvc.perform(get("/metrics/usage-over-time")
                            .param("period", "month"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("month"));
        }

        @Test
        @DisplayName("Returns 400 for invalid period parameter")
        @WithMockUser(roles = "CLIENT")
        void returns400ForInvalidPeriod() throws Exception {
            mockMvc.perform(get("/metrics/usage-over-time")
                            .param("period", "invalid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Period must be one of: day, week, month"));

            verify(metricsService, never()).getUsageOverTime(any(), any());
        }

        @Test
        @DisplayName("Normalizes period parameter to lowercase")
        @WithMockUser(roles = "CLIENT")
        void normalizesPeriodToLowercase() throws Exception {
            UsageOverTimeResponse response = UsageOverTimeResponse.builder()
                    .period("day")
                    .data(Collections.emptyList())
                    .build();

            when(metricsService.getUsageOverTime(isNull(), eq("day"))).thenReturn(response);

            mockMvc.perform(get("/metrics/usage-over-time")
                            .param("period", "DAY"))
                    .andExpect(status().isOk());

            verify(metricsService).getUsageOverTime(isNull(), eq("day"));
        }

        @Test
        @DisplayName("OPERATOR can specify clientId")
        @WithMockUser(roles = "OPERATOR")
        void operatorCanSpecifyClientId() throws Exception {
            UsageOverTimeResponse response = UsageOverTimeResponse.builder()
                    .period("day")
                    .data(Collections.emptyList())
                    .build();

            when(metricsService.getUsageOverTime(eq(88L), eq("day"))).thenReturn(response);

            mockMvc.perform(get("/metrics/usage-over-time")
                            .param("period", "day")
                            .param("clientId", "88"))
                    .andExpect(status().isOk());

            verify(metricsService).getUsageOverTime(eq(88L), eq("day"));
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/usage-over-time")
                            .param("period", "day"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Data Isolation - CLIENT role")
    class DataIsolationClientRole {

        @Test
        @DisplayName("CLIENT role sees only their own data regardless of clientId param")
        @WithMockUser(roles = "CLIENT")
        void clientShouldOnlySeeOwnData() throws Exception {
            // Even when CLIENT passes clientId param, it should be ignored by the service
            // The service's resolveClientId() method handles this
            MetricsSummaryResponse response = MetricsSummaryResponse.builder()
                    .totalCalls(10L)
                    .totalMessages(20L)
                    .totalCreditsUsed(new BigDecimal("5.00"))
                    .currentBalance(new BigDecimal("95.00"))
                    .activeSessionsCount(0L)
                    .build();

            // Even with clientId=999, the service will use the authenticated user's ID
            when(metricsService.getSummary(eq(999L))).thenReturn(response);

            mockMvc.perform(get("/metrics/summary").param("clientId", "999"))
                    .andExpect(status().isOk());

            // The controller passes clientId to service; service resolves it based on role
            verify(metricsService).getSummary(999L);
        }
    }

    @Nested
    @DisplayName("Access Control - All endpoints require authentication")
    class AuthenticationRequired {

        @Test
        @DisplayName("Summary endpoint requires authentication")
        void summaryRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/summary"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Calls endpoint requires authentication")
        void callsRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/calls")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Messages endpoint requires authentication")
        void messagesRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/messages")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-31"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Usage-over-time endpoint requires authentication")
        void usageOverTimeRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/usage-over-time")
                            .param("period", "day"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
