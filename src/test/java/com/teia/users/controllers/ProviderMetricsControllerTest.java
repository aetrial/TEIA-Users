package com.teia.users.controllers;

import com.teia.users.dto.*;
import com.teia.users.exception.ForbiddenException;
import com.teia.users.services.ProviderMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
@DisplayName("ProviderMetricsController")
class ProviderMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderMetricsService providerMetricsService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProviderSummaryResponse buildZeroSummary() {
        return ProviderSummaryResponse.builder()
                .twilio(ProviderSummaryResponse.TwilioSummary.builder()
                        .totalCallMinutes(0L).totalSmsSent(0L).build())
                .openAi(ProviderSummaryResponse.OpenAiSummary.builder()
                        .totalInputTokens(0L).totalOutputTokens(0L).build())
                .elevenLabs(ProviderSummaryResponse.ElevenLabsSummary.builder()
                        .totalCharacters(0L).build())
                .stripe(ProviderSummaryResponse.StripeSummary.builder()
                        .totalPaid(BigDecimal.ZERO).currency("usd").lastPaymentAt(null).build())
                .build();
    }

    private ProviderSummaryResponse buildNonZeroSummary() {
        return ProviderSummaryResponse.builder()
                .twilio(ProviderSummaryResponse.TwilioSummary.builder()
                        .totalCallMinutes(3L).totalSmsSent(5L).build())
                .openAi(ProviderSummaryResponse.OpenAiSummary.builder()
                        .totalInputTokens(1000L).totalOutputTokens(500L).build())
                .elevenLabs(ProviderSummaryResponse.ElevenLabsSummary.builder()
                        .totalCharacters(2000L).build())
                .stripe(ProviderSummaryResponse.StripeSummary.builder()
                        .totalPaid(new BigDecimal("50.00")).currency("usd")
                        .lastPaymentAt(LocalDateTime.of(2026, 3, 20, 10, 0)).build())
                .build();
    }

    private TwilioMetricsResponse buildTwilioResponse(boolean withData) {
        if (!withData) {
            return TwilioMetricsResponse.builder()
                    .sessions(Collections.emptyList())
                    .aggregate(TwilioMetricsResponse.TwilioAggregate.builder()
                            .totalCallMinutes(0L).totalSmsSent(0L).build())
                    .build();
        }
        return TwilioMetricsResponse.builder()
                .sessions(Arrays.asList(
                        TwilioMetricsResponse.TwilioSessionMetric.builder()
                                .sessionId("sess_001")
                                .callDurationSeconds(180)
                                .smsCount(2)
                                .timestamp(LocalDateTime.of(2026, 3, 15, 10, 0))
                                .build()
                ))
                .aggregate(TwilioMetricsResponse.TwilioAggregate.builder()
                        .totalCallMinutes(3L).totalSmsSent(2L).build())
                .build();
    }

    private OpenAiMetricsResponse buildOpenAiResponse(boolean withData) {
        if (!withData) {
            return OpenAiMetricsResponse.builder()
                    .sessions(Collections.emptyList())
                    .aggregate(OpenAiMetricsResponse.OpenAiAggregate.builder()
                            .totalInputTokens(0L).totalOutputTokens(0L).build())
                    .build();
        }
        return OpenAiMetricsResponse.builder()
                .sessions(Arrays.asList(
                        OpenAiMetricsResponse.OpenAiSessionMetric.builder()
                                .sessionId("sess_001")
                                .inputTokens(1000)
                                .outputTokens(500)
                                .timestamp(LocalDateTime.of(2026, 3, 15, 10, 0))
                                .build()
                ))
                .aggregate(OpenAiMetricsResponse.OpenAiAggregate.builder()
                        .totalInputTokens(1000L).totalOutputTokens(500L).build())
                .build();
    }

    private ElevenLabsMetricsResponse buildElevenLabsResponse(boolean withData) {
        if (!withData) {
            return ElevenLabsMetricsResponse.builder()
                    .sessions(Collections.emptyList())
                    .aggregate(ElevenLabsMetricsResponse.ElevenLabsAggregate.builder()
                            .totalCharacters(0L).build())
                    .build();
        }
        return ElevenLabsMetricsResponse.builder()
                .sessions(Arrays.asList(
                        ElevenLabsMetricsResponse.ElevenLabsSessionMetric.builder()
                                .sessionId("sess_001")
                                .characters(2000)
                                .timestamp(LocalDateTime.of(2026, 3, 15, 10, 0))
                                .build()
                ))
                .aggregate(ElevenLabsMetricsResponse.ElevenLabsAggregate.builder()
                        .totalCharacters(2000L).build())
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /metrics/providers/summary
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /metrics/providers/summary")
    class SummaryEndpoint {

        @Test
        @DisplayName("Authenticated CLIENT receives summary metrics")
        @WithMockUser(roles = "CLIENT")
        void authenticatedClientReceivesSummary() throws Exception {
            when(providerMetricsService.getSummary(isNull(), any(), any()))
                    .thenReturn(buildNonZeroSummary());

            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.twilio.totalCallMinutes").value(3))
                    .andExpect(jsonPath("$.twilio.totalSmsSent").value(5))
                    .andExpect(jsonPath("$.openAi.totalInputTokens").value(1000))
                    .andExpect(jsonPath("$.openAi.totalOutputTokens").value(500))
                    .andExpect(jsonPath("$.elevenLabs.totalCharacters").value(2000))
                    .andExpect(jsonPath("$.stripe.totalPaid").value(50.00))
                    .andExpect(jsonPath("$.stripe.currency").value("usd"));

            verify(providerMetricsService).getSummary(isNull(), any(), any());
        }

        @Test
        @DisplayName("OPERATOR can specify clientId parameter")
        @WithMockUser(roles = "OPERATOR")
        void operatorCanSpecifyClientId() throws Exception {
            when(providerMetricsService.getSummary(eq(42L), any(), any()))
                    .thenReturn(buildNonZeroSummary());

            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27")
                            .param("clientId", "42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.twilio.totalCallMinutes").value(3));

            verify(providerMetricsService).getSummary(eq(42L), any(), any());
        }

        @Test
        @DisplayName("ADMIN can specify clientId parameter")
        @WithMockUser(roles = "ADMIN")
        void adminCanSpecifyClientId() throws Exception {
            when(providerMetricsService.getSummary(eq(99L), any(), any()))
                    .thenReturn(buildNonZeroSummary());

            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27")
                            .param("clientId", "99"))
                    .andExpect(status().isOk());

            verify(providerMetricsService).getSummary(eq(99L), any(), any());
        }

        @Test
        @DisplayName("CLIENT spoofing another clientId returns 403")
        @WithMockUser(roles = "CLIENT")
        void clientSpoofingAnotherClientIdReturns403() throws Exception {
            when(providerMetricsService.getSummary(eq(999L), any(), any()))
                    .thenThrow(new ForbiddenException("Clients may only access their own metrics."));

            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27")
                            .param("clientId", "999"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("Clients may only access their own metrics."));
        }

        @Test
        @DisplayName("Returns 400 when from date is after to date")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenFromAfterTo() throws Exception {
            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-31")
                            .param("to", "2026-03-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("'from' date must not be after 'to' date"));

            verify(providerMetricsService, never()).getSummary(any(), any(), any());
        }

        @Test
        @DisplayName("Returns 400 when missing from parameter")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenMissingFrom() throws Exception {
            mockMvc.perform(get("/metrics/providers/summary")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when missing to parameter")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenMissingTo() throws Exception {
            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-01"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Zero-activity client receives zeroed aggregates (not 404 or null)")
        @WithMockUser(roles = "CLIENT")
        void zeroActivityReturnsZeroedAggregates() throws Exception {
            when(providerMetricsService.getSummary(isNull(), any(), any()))
                    .thenReturn(buildZeroSummary());

            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.twilio.totalCallMinutes").value(0))
                    .andExpect(jsonPath("$.twilio.totalSmsSent").value(0))
                    .andExpect(jsonPath("$.openAi.totalInputTokens").value(0))
                    .andExpect(jsonPath("$.elevenLabs.totalCharacters").value(0))
                    .andExpect(jsonPath("$.stripe.totalPaid").value(0));
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isUnauthorized());

            verify(providerMetricsService, never()).getSummary(any(), any(), any());
        }

        @Test
        @DisplayName("Accepts single-day range when from equals to")
        @WithMockUser(roles = "CLIENT")
        void acceptsSingleDayRange() throws Exception {
            when(providerMetricsService.getSummary(isNull(), any(), any()))
                    .thenReturn(buildZeroSummary());

            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-27")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isOk());

            verify(providerMetricsService).getSummary(isNull(), any(), any());
        }
    }

    // -------------------------------------------------------------------------
    // GET /metrics/providers/twilio
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /metrics/providers/twilio")
    class TwilioEndpoint {

        @Test
        @DisplayName("Authenticated CLIENT receives Twilio metrics with sessions and aggregate")
        @WithMockUser(roles = "CLIENT")
        void authenticatedClientReceivesTwilioMetrics() throws Exception {
            when(providerMetricsService.getTwilioMetrics(isNull(), any(), any()))
                    .thenReturn(buildTwilioResponse(true));

            mockMvc.perform(get("/metrics/providers/twilio")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions.length()").value(1))
                    .andExpect(jsonPath("$.sessions[0].sessionId").value("sess_001"))
                    .andExpect(jsonPath("$.sessions[0].callDurationSeconds").value(180))
                    .andExpect(jsonPath("$.sessions[0].smsCount").value(2))
                    .andExpect(jsonPath("$.aggregate.totalCallMinutes").value(3))
                    .andExpect(jsonPath("$.aggregate.totalSmsSent").value(2));
        }

        @Test
        @DisplayName("CLIENT spoofing another clientId returns 403")
        @WithMockUser(roles = "CLIENT")
        void clientSpoofingReturns403() throws Exception {
            when(providerMetricsService.getTwilioMetrics(eq(999L), any(), any()))
                    .thenThrow(new ForbiddenException("Clients may only access their own metrics."));

            mockMvc.perform(get("/metrics/providers/twilio")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27")
                            .param("clientId", "999"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Returns 400 when from date is after to date")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenFromAfterTo() throws Exception {
            mockMvc.perform(get("/metrics/providers/twilio")
                            .param("from", "2026-12-31")
                            .param("to", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("'from' date must not be after 'to' date"));

            verify(providerMetricsService, never()).getTwilioMetrics(any(), any(), any());
        }

        @Test
        @DisplayName("Returns empty sessions with zeroed aggregate when no events")
        @WithMockUser(roles = "CLIENT")
        void returnsEmptySessionsWhenNoData() throws Exception {
            when(providerMetricsService.getTwilioMetrics(isNull(), any(), any()))
                    .thenReturn(buildTwilioResponse(false));

            mockMvc.perform(get("/metrics/providers/twilio")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions.length()").value(0))
                    .andExpect(jsonPath("$.aggregate.totalCallMinutes").value(0))
                    .andExpect(jsonPath("$.aggregate.totalSmsSent").value(0));
        }

        @Test
        @DisplayName("OPERATOR can specify clientId for Twilio metrics")
        @WithMockUser(roles = "OPERATOR")
        void operatorCanSpecifyClientId() throws Exception {
            when(providerMetricsService.getTwilioMetrics(eq(55L), any(), any()))
                    .thenReturn(buildTwilioResponse(false));

            mockMvc.perform(get("/metrics/providers/twilio")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27")
                            .param("clientId", "55"))
                    .andExpect(status().isOk());

            verify(providerMetricsService).getTwilioMetrics(eq(55L), any(), any());
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/providers/twilio")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -------------------------------------------------------------------------
    // GET /metrics/providers/openai
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /metrics/providers/openai")
    class OpenAiEndpoint {

        @Test
        @DisplayName("Authenticated CLIENT receives OpenAI metrics with sessions and aggregate")
        @WithMockUser(roles = "CLIENT")
        void authenticatedClientReceivesOpenAiMetrics() throws Exception {
            when(providerMetricsService.getOpenAiMetrics(isNull(), any(), any()))
                    .thenReturn(buildOpenAiResponse(true));

            mockMvc.perform(get("/metrics/providers/openai")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions.length()").value(1))
                    .andExpect(jsonPath("$.sessions[0].sessionId").value("sess_001"))
                    .andExpect(jsonPath("$.sessions[0].inputTokens").value(1000))
                    .andExpect(jsonPath("$.sessions[0].outputTokens").value(500))
                    .andExpect(jsonPath("$.aggregate.totalInputTokens").value(1000))
                    .andExpect(jsonPath("$.aggregate.totalOutputTokens").value(500));
        }

        @Test
        @DisplayName("CLIENT spoofing another clientId returns 403")
        @WithMockUser(roles = "CLIENT")
        void clientSpoofingReturns403() throws Exception {
            when(providerMetricsService.getOpenAiMetrics(eq(999L), any(), any()))
                    .thenThrow(new ForbiddenException("Clients may only access their own metrics."));

            mockMvc.perform(get("/metrics/providers/openai")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27")
                            .param("clientId", "999"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Returns 400 when from date is after to date")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenFromAfterTo() throws Exception {
            mockMvc.perform(get("/metrics/providers/openai")
                            .param("from", "2026-12-31")
                            .param("to", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("'from' date must not be after 'to' date"));

            verify(providerMetricsService, never()).getOpenAiMetrics(any(), any(), any());
        }

        @Test
        @DisplayName("Returns empty sessions with zeroed aggregate when no events")
        @WithMockUser(roles = "CLIENT")
        void returnsEmptySessionsWhenNoData() throws Exception {
            when(providerMetricsService.getOpenAiMetrics(isNull(), any(), any()))
                    .thenReturn(buildOpenAiResponse(false));

            mockMvc.perform(get("/metrics/providers/openai")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions.length()").value(0))
                    .andExpect(jsonPath("$.aggregate.totalInputTokens").value(0))
                    .andExpect(jsonPath("$.aggregate.totalOutputTokens").value(0));
        }

        @Test
        @DisplayName("ADMIN can specify clientId for OpenAI metrics")
        @WithMockUser(roles = "ADMIN")
        void adminCanSpecifyClientId() throws Exception {
            when(providerMetricsService.getOpenAiMetrics(eq(77L), any(), any()))
                    .thenReturn(buildOpenAiResponse(false));

            mockMvc.perform(get("/metrics/providers/openai")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27")
                            .param("clientId", "77"))
                    .andExpect(status().isOk());

            verify(providerMetricsService).getOpenAiMetrics(eq(77L), any(), any());
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/providers/openai")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -------------------------------------------------------------------------
    // GET /metrics/providers/elevenlabs
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /metrics/providers/elevenlabs")
    class ElevenLabsEndpoint {

        @Test
        @DisplayName("Authenticated CLIENT receives ElevenLabs metrics with sessions and aggregate")
        @WithMockUser(roles = "CLIENT")
        void authenticatedClientReceivesElevenLabsMetrics() throws Exception {
            when(providerMetricsService.getElevenLabsMetrics(isNull(), any(), any()))
                    .thenReturn(buildElevenLabsResponse(true));

            mockMvc.perform(get("/metrics/providers/elevenlabs")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions.length()").value(1))
                    .andExpect(jsonPath("$.sessions[0].sessionId").value("sess_001"))
                    .andExpect(jsonPath("$.sessions[0].characters").value(2000))
                    .andExpect(jsonPath("$.aggregate.totalCharacters").value(2000));
        }

        @Test
        @DisplayName("CLIENT spoofing another clientId returns 403")
        @WithMockUser(roles = "CLIENT")
        void clientSpoofingReturns403() throws Exception {
            when(providerMetricsService.getElevenLabsMetrics(eq(999L), any(), any()))
                    .thenThrow(new ForbiddenException("Clients may only access their own metrics."));

            mockMvc.perform(get("/metrics/providers/elevenlabs")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27")
                            .param("clientId", "999"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Returns 400 when from date is after to date")
        @WithMockUser(roles = "CLIENT")
        void returns400WhenFromAfterTo() throws Exception {
            mockMvc.perform(get("/metrics/providers/elevenlabs")
                            .param("from", "2026-12-31")
                            .param("to", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("'from' date must not be after 'to' date"));

            verify(providerMetricsService, never()).getElevenLabsMetrics(any(), any(), any());
        }

        @Test
        @DisplayName("Returns empty sessions with zeroed aggregate when no events")
        @WithMockUser(roles = "CLIENT")
        void returnsEmptySessionsWhenNoData() throws Exception {
            when(providerMetricsService.getElevenLabsMetrics(isNull(), any(), any()))
                    .thenReturn(buildElevenLabsResponse(false));

            mockMvc.perform(get("/metrics/providers/elevenlabs")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessions.length()").value(0))
                    .andExpect(jsonPath("$.aggregate.totalCharacters").value(0));
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/providers/elevenlabs")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -------------------------------------------------------------------------
    // GET /metrics/providers/stripe
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /metrics/providers/stripe")
    class StripeEndpoint {

        @Test
        @DisplayName("Authenticated CLIENT receives Stripe payment history")
        @WithMockUser(roles = "CLIENT")
        void authenticatedClientReceivesPayments() throws Exception {
            List<StripePaymentResponse> payments = Arrays.asList(
                    StripePaymentResponse.builder()
                            .paymentIntentId("pi_001")
                            .amount(new BigDecimal("50.00"))
                            .currency("usd")
                            .status("succeeded")
                            .creditsPurchased(new BigDecimal("50.00"))
                            .createdAt(LocalDateTime.of(2026, 3, 20, 10, 0))
                            .build(),
                    StripePaymentResponse.builder()
                            .paymentIntentId("pi_002")
                            .amount(new BigDecimal("25.00"))
                            .currency("usd")
                            .status("succeeded")
                            .creditsPurchased(new BigDecimal("25.00"))
                            .createdAt(LocalDateTime.of(2026, 3, 10, 15, 0))
                            .build()
            );

            when(providerMetricsService.getStripePayments(isNull())).thenReturn(payments);

            mockMvc.perform(get("/metrics/providers/stripe"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].paymentIntentId").value("pi_001"))
                    .andExpect(jsonPath("$[0].amount").value(50.00))
                    .andExpect(jsonPath("$[0].currency").value("usd"))
                    .andExpect(jsonPath("$[0].status").value("succeeded"))
                    .andExpect(jsonPath("$[1].paymentIntentId").value("pi_002"));

            verify(providerMetricsService).getStripePayments(isNull());
        }

        @Test
        @DisplayName("CLIENT spoofing another clientId returns 403")
        @WithMockUser(roles = "CLIENT")
        void clientSpoofingReturns403() throws Exception {
            when(providerMetricsService.getStripePayments(eq(999L)))
                    .thenThrow(new ForbiddenException("Clients may only access their own metrics."));

            mockMvc.perform(get("/metrics/providers/stripe")
                            .param("clientId", "999"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("OPERATOR can specify clientId for Stripe payments")
        @WithMockUser(roles = "OPERATOR")
        void operatorCanSpecifyClientId() throws Exception {
            when(providerMetricsService.getStripePayments(eq(42L)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/metrics/providers/stripe")
                            .param("clientId", "42"))
                    .andExpect(status().isOk());

            verify(providerMetricsService).getStripePayments(eq(42L));
        }

        @Test
        @DisplayName("Returns empty list when no payments exist")
        @WithMockUser(roles = "CLIENT")
        void returnsEmptyListWhenNoPayments() throws Exception {
            when(providerMetricsService.getStripePayments(isNull()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/metrics/providers/stripe"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/metrics/providers/stripe"))
                    .andExpect(status().isUnauthorized());

            verify(providerMetricsService, never()).getStripePayments(any());
        }
    }

    // -------------------------------------------------------------------------
    // All endpoints require authentication
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Access Control - All endpoints require authentication")
    class AuthenticationRequired {

        @Test
        @DisplayName("Summary endpoint requires authentication")
        void summaryRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/providers/summary")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Twilio endpoint requires authentication")
        void twilioRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/providers/twilio")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("OpenAI endpoint requires authentication")
        void openAiRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/providers/openai")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ElevenLabs endpoint requires authentication")
        void elevenLabsRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/providers/elevenlabs")
                            .param("from", "2026-03-01")
                            .param("to", "2026-03-27"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Stripe endpoint requires authentication")
        void stripeRequiresAuth() throws Exception {
            mockMvc.perform(get("/metrics/providers/stripe"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
