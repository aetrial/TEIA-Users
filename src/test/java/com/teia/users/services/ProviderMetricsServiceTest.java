package com.teia.users.services;

import com.teia.users.dto.*;
import com.teia.users.exception.ForbiddenException;
import com.teia.users.models.CreditTransaction;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.UsageEvent;
import com.teia.users.models.enums.CreditTransactionType;
import com.teia.users.models.enums.Role;
import com.teia.users.repositories.CreditTransactionRepository;
import com.teia.users.repositories.UsageEventRepository;
import com.teia.users.repositories.UserRepository;
import com.teia.users.security.JwtUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderMetricsService")
class ProviderMetricsServiceTest {

    @Mock
    private UsageEventRepository usageEventRepository;

    @Mock
    private CreditTransactionRepository creditTransactionRepository;

    @Mock
    private UserRepository userRepository;

    private ProviderMetricsService service;

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 3, 1, 0, 0);
    private static final LocalDateTime TO   = LocalDateTime.of(2026, 4, 1, 0, 0);

    @BeforeEach
    void setUp() {
        service = new ProviderMetricsService(usageEventRepository, creditTransactionRepository, userRepository);
        // Prevent NullPointerException when stripeSecretKey.isBlank() is evaluated
        ReflectionTestUtils.setField(service, "stripeSecretKey", "");
        SecurityContextHolder.clearContext();
    }

    private void setAuth(Long userId, Role role) {
        JwtUserPrincipal principal = new JwtUserPrincipal(userId, "test@example.com", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private UsageEvent buildEvent(String sessionId,
                                   Integer twilioCallSecs, Integer twilioSms,
                                   Integer openAiIn, Integer openAiOut,
                                   Integer elevenLabsChars) {
        UsageEvent e = new UsageEvent();
        e.setSessionId(sessionId);
        e.setClientId(100L);
        e.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        e.setTwilioCallDurationSeconds(twilioCallSecs);
        e.setTwilioSmsCount(twilioSms);
        e.setOpenAiInputTokens(openAiIn);
        e.setOpenAiOutputTokens(openAiOut);
        e.setElevenLabsCharacters(elevenLabsChars);
        return e;
    }

    private CreditTransaction buildPurchaseTx(Long clientId, String referenceId, BigDecimal amount,
                                               LocalDateTime createdAt) {
        CreditTransaction tx = new CreditTransaction(clientId, amount, CreditTransactionType.PURCHASE, referenceId);
        // Override createdAt (constructor sets it to now)
        ReflectionTestUtils.setField(tx, "createdAt", createdAt);
        return tx;
    }

    // -------------------------------------------------------------------------
    // resolveClientIdStrict
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("resolveClientIdStrict - Data Isolation Logic")
    class ResolveClientIdStrict {

        @Test
        @DisplayName("CLIENT role uses own userId when null is requested")
        void clientRoleUsesOwnIdWhenNullRequested() {
            setAuth(100L, Role.CLIENT);
            assertEquals(100L, service.resolveClientIdStrict(null));
        }

        @Test
        @DisplayName("CLIENT role uses own userId when same ID is explicitly passed")
        void clientRoleUsesOwnIdWhenSameIdRequested() {
            setAuth(100L, Role.CLIENT);
            assertEquals(100L, service.resolveClientIdStrict(100L));
        }

        @Test
        @DisplayName("CLIENT role throws ForbiddenException when a different clientId is requested")
        void clientRoleThrowsForbiddenForDifferentClientId() {
            setAuth(100L, Role.CLIENT);
            assertThrows(ForbiddenException.class, () -> service.resolveClientIdStrict(999L));
        }

        @Test
        @DisplayName("OPERATOR role uses the requested clientId when provided")
        void operatorUsesRequestedClientId() {
            setAuth(50L, Role.OPERATOR);
            assertEquals(200L, service.resolveClientIdStrict(200L));
        }

        @Test
        @DisplayName("OPERATOR role uses own userId when null is requested")
        void operatorUsesOwnIdWhenNullRequested() {
            setAuth(50L, Role.OPERATOR);
            assertEquals(50L, service.resolveClientIdStrict(null));
        }

        @Test
        @DisplayName("ADMIN role uses the requested clientId when provided")
        void adminUsesRequestedClientId() {
            setAuth(1L, Role.ADMIN);
            assertEquals(300L, service.resolveClientIdStrict(300L));
        }

        @Test
        @DisplayName("ADMIN role uses own userId when null is requested")
        void adminUsesOwnIdWhenNullRequested() {
            setAuth(1L, Role.ADMIN);
            assertEquals(1L, service.resolveClientIdStrict(null));
        }
    }

    // -------------------------------------------------------------------------
    // getSummary
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("Returns aggregated totals across all providers")
        void returnsAggregatedTotals() {
            setAuth(100L, Role.CLIENT);

            when(usageEventRepository.sumTwilioCallDurationSeconds(100L, FROM, TO)).thenReturn(180L);
            when(usageEventRepository.sumTwilioSmsCount(100L, FROM, TO)).thenReturn(5L);
            when(usageEventRepository.sumOpenAiInputTokens(100L, FROM, TO)).thenReturn(1000L);
            when(usageEventRepository.sumOpenAiOutputTokens(100L, FROM, TO)).thenReturn(500L);
            when(usageEventRepository.sumElevenLabsCharacters(100L, FROM, TO)).thenReturn(2000L);

            CreditTransaction tx = buildPurchaseTx(100L, "pi_001", new BigDecimal("50.00"),
                    LocalDateTime.of(2026, 3, 20, 10, 0));
            when(creditTransactionRepository.findByClientIdAndType(100L, CreditTransactionType.PURCHASE))
                    .thenReturn(Collections.singletonList(tx));

            ProviderSummaryResponse response = service.getSummary(null, FROM, TO);

            // Twilio: 180 seconds → ceiling-rounded minutes = 3
            assertEquals(3L, response.getTwilio().getTotalCallMinutes());
            assertEquals(5L, response.getTwilio().getTotalSmsSent());
            assertEquals(1000L, response.getOpenAi().getTotalInputTokens());
            assertEquals(500L, response.getOpenAi().getTotalOutputTokens());
            assertEquals(2000L, response.getElevenLabs().getTotalCharacters());
            assertEquals(new BigDecimal("50.00"), response.getStripe().getTotalPaid());
            assertEquals("usd", response.getStripe().getCurrency());
            assertNotNull(response.getStripe().getLastPaymentAt());
        }

        @Test
        @DisplayName("Zero-activity client receives zeroed aggregates (not null)")
        void zeroActivityReturnsZeroedAggregates() {
            setAuth(100L, Role.CLIENT);

            when(usageEventRepository.sumTwilioCallDurationSeconds(100L, FROM, TO)).thenReturn(0L);
            when(usageEventRepository.sumTwilioSmsCount(100L, FROM, TO)).thenReturn(0L);
            when(usageEventRepository.sumOpenAiInputTokens(100L, FROM, TO)).thenReturn(0L);
            when(usageEventRepository.sumOpenAiOutputTokens(100L, FROM, TO)).thenReturn(0L);
            when(usageEventRepository.sumElevenLabsCharacters(100L, FROM, TO)).thenReturn(0L);
            when(creditTransactionRepository.findByClientIdAndType(100L, CreditTransactionType.PURCHASE))
                    .thenReturn(Collections.emptyList());

            ProviderSummaryResponse response = service.getSummary(null, FROM, TO);

            assertNotNull(response);
            assertEquals(0L, response.getTwilio().getTotalCallMinutes());
            assertEquals(0L, response.getTwilio().getTotalSmsSent());
            assertEquals(0L, response.getOpenAi().getTotalInputTokens());
            assertEquals(0L, response.getOpenAi().getTotalOutputTokens());
            assertEquals(0L, response.getElevenLabs().getTotalCharacters());
            assertEquals(BigDecimal.ZERO, response.getStripe().getTotalPaid());
            assertNull(response.getStripe().getLastPaymentAt());
        }

        @Test
        @DisplayName("Multiple purchase transactions sum correctly and last payment is most recent")
        void multiplePurchasesAggregateCorrectly() {
            setAuth(100L, Role.CLIENT);

            when(usageEventRepository.sumTwilioCallDurationSeconds(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumTwilioSmsCount(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumOpenAiInputTokens(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumOpenAiOutputTokens(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumElevenLabsCharacters(any(), any(), any())).thenReturn(0L);

            LocalDateTime earlier = LocalDateTime.of(2026, 3, 10, 10, 0);
            LocalDateTime later   = LocalDateTime.of(2026, 3, 20, 10, 0);
            List<CreditTransaction> txList = Arrays.asList(
                    buildPurchaseTx(100L, "pi_001", new BigDecimal("30.00"), earlier),
                    buildPurchaseTx(100L, "pi_002", new BigDecimal("20.00"), later)
            );
            when(creditTransactionRepository.findByClientIdAndType(100L, CreditTransactionType.PURCHASE))
                    .thenReturn(txList);

            ProviderSummaryResponse response = service.getSummary(null, FROM, TO);

            assertEquals(new BigDecimal("50.00"), response.getStripe().getTotalPaid());
            assertEquals(later, response.getStripe().getLastPaymentAt());
        }

        @Test
        @DisplayName("Seconds-to-minutes uses ceiling rounding")
        void secondsToMinutesCeilingRounding() {
            setAuth(100L, Role.CLIENT);

            // 61 seconds → 2 minutes (ceiling)
            when(usageEventRepository.sumTwilioCallDurationSeconds(100L, FROM, TO)).thenReturn(61L);
            when(usageEventRepository.sumTwilioSmsCount(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumOpenAiInputTokens(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumOpenAiOutputTokens(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumElevenLabsCharacters(any(), any(), any())).thenReturn(0L);
            when(creditTransactionRepository.findByClientIdAndType(100L, CreditTransactionType.PURCHASE))
                    .thenReturn(Collections.emptyList());

            ProviderSummaryResponse response = service.getSummary(null, FROM, TO);

            assertEquals(2L, response.getTwilio().getTotalCallMinutes());
        }
    }

    // -------------------------------------------------------------------------
    // getTwilioMetrics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getTwilioMetrics")
    class GetTwilioMetrics {

        @Test
        @DisplayName("Returns per-session data and correct aggregate")
        void returnsPerSessionDataAndAggregate() {
            setAuth(100L, Role.CLIENT);

            UsageEvent event = buildEvent("sess_001", 180, 2, null, null, null);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.singletonList(event));

            TwilioMetricsResponse response = service.getTwilioMetrics(null, FROM, TO);

            assertEquals(1, response.getSessions().size());
            assertEquals("sess_001", response.getSessions().get(0).getSessionId());
            assertEquals(Integer.valueOf(180), response.getSessions().get(0).getCallDurationSeconds());
            assertEquals(Integer.valueOf(2), response.getSessions().get(0).getSmsCount());
            // 180 seconds → 3 minutes
            assertEquals(3L, response.getAggregate().getTotalCallMinutes());
            assertEquals(2L, response.getAggregate().getTotalSmsSent());
        }

        @Test
        @DisplayName("Returns empty sessions with zeroed aggregate when no events")
        void returnsZeroedAggregateWhenNoEvents() {
            setAuth(100L, Role.CLIENT);

            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.emptyList());

            TwilioMetricsResponse response = service.getTwilioMetrics(null, FROM, TO);

            assertNotNull(response);
            assertTrue(response.getSessions().isEmpty());
            assertEquals(0L, response.getAggregate().getTotalCallMinutes());
            assertEquals(0L, response.getAggregate().getTotalSmsSent());
        }

        @Test
        @DisplayName("Null telemetry values are treated as 0 in the aggregate")
        void nullTelemetryValuesAreZero() {
            setAuth(100L, Role.CLIENT);

            UsageEvent event = buildEvent("sess_null", null, null, null, null, null);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.singletonList(event));

            TwilioMetricsResponse response = service.getTwilioMetrics(null, FROM, TO);

            assertEquals(1, response.getSessions().size());
            assertNull(response.getSessions().get(0).getCallDurationSeconds());
            assertNull(response.getSessions().get(0).getSmsCount());
            assertEquals(0L, response.getAggregate().getTotalCallMinutes());
            assertEquals(0L, response.getAggregate().getTotalSmsSent());
        }

        @Test
        @DisplayName("Multiple sessions aggregate correctly")
        void multipleSessionsAggregateCorrectly() {
            setAuth(100L, Role.CLIENT);

            UsageEvent e1 = buildEvent("sess_001", 60, 1, null, null, null);
            UsageEvent e2 = buildEvent("sess_002", 120, 3, null, null, null);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Arrays.asList(e1, e2));

            TwilioMetricsResponse response = service.getTwilioMetrics(null, FROM, TO);

            assertEquals(2, response.getSessions().size());
            // 60 + 120 = 180 seconds → 3 minutes
            assertEquals(3L, response.getAggregate().getTotalCallMinutes());
            assertEquals(4L, response.getAggregate().getTotalSmsSent());
        }

        @Test
        @DisplayName("OPERATOR can retrieve another client's Twilio metrics")
        void operatorCanAccessAnotherClient() {
            setAuth(50L, Role.OPERATOR);

            when(usageEventRepository.findByClientIdAndDateRange(200L, FROM, TO))
                    .thenReturn(Collections.emptyList());

            TwilioMetricsResponse response = service.getTwilioMetrics(200L, FROM, TO);

            assertNotNull(response);
            verify(usageEventRepository).findByClientIdAndDateRange(200L, FROM, TO);
        }
    }

    // -------------------------------------------------------------------------
    // getOpenAiMetrics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getOpenAiMetrics")
    class GetOpenAiMetrics {

        @Test
        @DisplayName("Returns per-session token data and correct aggregate")
        void returnsPerSessionDataAndAggregate() {
            setAuth(100L, Role.CLIENT);

            UsageEvent event = buildEvent("sess_001", null, null, 1000, 500, null);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.singletonList(event));

            OpenAiMetricsResponse response = service.getOpenAiMetrics(null, FROM, TO);

            assertEquals(1, response.getSessions().size());
            assertEquals("sess_001", response.getSessions().get(0).getSessionId());
            assertEquals(Integer.valueOf(1000), response.getSessions().get(0).getInputTokens());
            assertEquals(Integer.valueOf(500), response.getSessions().get(0).getOutputTokens());
            assertEquals(1000L, response.getAggregate().getTotalInputTokens());
            assertEquals(500L, response.getAggregate().getTotalOutputTokens());
        }

        @Test
        @DisplayName("Returns empty sessions with zeroed aggregate when no events")
        void returnsZeroedAggregateWhenNoEvents() {
            setAuth(100L, Role.CLIENT);

            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.emptyList());

            OpenAiMetricsResponse response = service.getOpenAiMetrics(null, FROM, TO);

            assertNotNull(response);
            assertTrue(response.getSessions().isEmpty());
            assertEquals(0L, response.getAggregate().getTotalInputTokens());
            assertEquals(0L, response.getAggregate().getTotalOutputTokens());
        }

        @Test
        @DisplayName("Null token values are treated as 0 in the aggregate")
        void nullTokenValuesAreZero() {
            setAuth(100L, Role.CLIENT);

            UsageEvent event = buildEvent("sess_null", null, null, null, null, null);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.singletonList(event));

            OpenAiMetricsResponse response = service.getOpenAiMetrics(null, FROM, TO);

            assertNull(response.getSessions().get(0).getInputTokens());
            assertNull(response.getSessions().get(0).getOutputTokens());
            assertEquals(0L, response.getAggregate().getTotalInputTokens());
            assertEquals(0L, response.getAggregate().getTotalOutputTokens());
        }

        @Test
        @DisplayName("Multiple sessions sum tokens correctly")
        void multipleSessionsSumTokens() {
            setAuth(100L, Role.CLIENT);

            UsageEvent e1 = buildEvent("sess_001", null, null, 600, 300, null);
            UsageEvent e2 = buildEvent("sess_002", null, null, 400, 200, null);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Arrays.asList(e1, e2));

            OpenAiMetricsResponse response = service.getOpenAiMetrics(null, FROM, TO);

            assertEquals(1000L, response.getAggregate().getTotalInputTokens());
            assertEquals(500L, response.getAggregate().getTotalOutputTokens());
        }
    }

    // -------------------------------------------------------------------------
    // getElevenLabsMetrics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getElevenLabsMetrics")
    class GetElevenLabsMetrics {

        @Test
        @DisplayName("Returns per-session character data and correct aggregate")
        void returnsPerSessionDataAndAggregate() {
            setAuth(100L, Role.CLIENT);

            UsageEvent event = buildEvent("sess_001", null, null, null, null, 2000);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.singletonList(event));

            ElevenLabsMetricsResponse response = service.getElevenLabsMetrics(null, FROM, TO);

            assertEquals(1, response.getSessions().size());
            assertEquals("sess_001", response.getSessions().get(0).getSessionId());
            assertEquals(Integer.valueOf(2000), response.getSessions().get(0).getCharacters());
            assertEquals(2000L, response.getAggregate().getTotalCharacters());
        }

        @Test
        @DisplayName("Returns empty sessions with zeroed aggregate when no events")
        void returnsZeroedAggregateWhenNoEvents() {
            setAuth(100L, Role.CLIENT);

            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.emptyList());

            ElevenLabsMetricsResponse response = service.getElevenLabsMetrics(null, FROM, TO);

            assertNotNull(response);
            assertTrue(response.getSessions().isEmpty());
            assertEquals(0L, response.getAggregate().getTotalCharacters());
        }

        @Test
        @DisplayName("Null character values are treated as 0 in the aggregate")
        void nullCharacterValuesAreZero() {
            setAuth(100L, Role.CLIENT);

            UsageEvent event = buildEvent("sess_null", null, null, null, null, null);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Collections.singletonList(event));

            ElevenLabsMetricsResponse response = service.getElevenLabsMetrics(null, FROM, TO);

            assertNull(response.getSessions().get(0).getCharacters());
            assertEquals(0L, response.getAggregate().getTotalCharacters());
        }

        @Test
        @DisplayName("Multiple sessions sum characters correctly")
        void multipleSessionsSumCharacters() {
            setAuth(100L, Role.CLIENT);

            UsageEvent e1 = buildEvent("sess_001", null, null, null, null, 1500);
            UsageEvent e2 = buildEvent("sess_002", null, null, null, null, 500);
            when(usageEventRepository.findByClientIdAndDateRange(100L, FROM, TO))
                    .thenReturn(Arrays.asList(e1, e2));

            ElevenLabsMetricsResponse response = service.getElevenLabsMetrics(null, FROM, TO);

            assertEquals(2000L, response.getAggregate().getTotalCharacters());
        }
    }

    // -------------------------------------------------------------------------
    // getStripePayments
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getStripePayments")
    class GetStripePayments {

        @Test
        @DisplayName("Returns local CreditTransaction records as StripePaymentResponse entries")
        void returnsLocalTransactions() {
            setAuth(100L, Role.CLIENT);

            LocalDateTime ts = LocalDateTime.of(2026, 3, 20, 10, 0);
            CreditTransaction tx = buildPurchaseTx(100L, "pi_001", new BigDecimal("50.00"), ts);
            when(creditTransactionRepository.findByClientIdAndTypeOrderByCreatedAtDesc(
                    eq(100L), eq(CreditTransactionType.PURCHASE), any()))
                    .thenReturn(Collections.singletonList(tx));
            when(userRepository.findById(100L)).thenReturn(Optional.empty());

            List<StripePaymentResponse> result = service.getStripePayments(null);

            assertEquals(1, result.size());
            assertEquals("pi_001", result.get(0).getPaymentIntentId());
            assertEquals(new BigDecimal("50.00"), result.get(0).getAmount());
            assertEquals("usd", result.get(0).getCurrency());
            assertEquals("succeeded", result.get(0).getStatus());
            assertEquals(new BigDecimal("50.00"), result.get(0).getCreditsPurchased());
        }

        @Test
        @DisplayName("Returns empty list when no transactions exist")
        void returnsEmptyListWhenNoTransactions() {
            setAuth(100L, Role.CLIENT);

            when(creditTransactionRepository.findByClientIdAndTypeOrderByCreatedAtDesc(
                    eq(100L), eq(CreditTransactionType.PURCHASE), any()))
                    .thenReturn(Collections.emptyList());
            when(userRepository.findById(100L)).thenReturn(Optional.empty());

            List<StripePaymentResponse> result = service.getStripePayments(null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Skips Stripe API call when user has no stripeCustomerId")
        void skipsStripeApiWhenNoCustomerId() {
            setAuth(100L, Role.CLIENT);

            when(creditTransactionRepository.findByClientIdAndTypeOrderByCreatedAtDesc(
                    eq(100L), eq(CreditTransactionType.PURCHASE), any()))
                    .thenReturn(Collections.emptyList());

            TeiaUser user = new TeiaUser();
            user.setStripeCustomerId(null);
            when(userRepository.findById(100L)).thenReturn(Optional.of(user));

            // Should not throw and should return empty list (no Stripe call made since key is empty)
            List<StripePaymentResponse> result = service.getStripePayments(null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Skips Stripe API call when user has blank stripeCustomerId")
        void skipsStripeApiWhenBlankCustomerId() {
            setAuth(100L, Role.CLIENT);

            when(creditTransactionRepository.findByClientIdAndTypeOrderByCreatedAtDesc(
                    eq(100L), eq(CreditTransactionType.PURCHASE), any()))
                    .thenReturn(Collections.emptyList());

            TeiaUser user = new TeiaUser();
            user.setStripeCustomerId("   ");
            when(userRepository.findById(100L)).thenReturn(Optional.of(user));

            List<StripePaymentResponse> result = service.getStripePayments(null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Transaction without referenceId is still included in results")
        void transactionWithoutReferenceIdIsIncluded() {
            setAuth(100L, Role.CLIENT);

            // tx with null referenceId — should get synthetic key "local_<id>"
            CreditTransaction tx = new CreditTransaction(100L, new BigDecimal("25.00"),
                    CreditTransactionType.PURCHASE, null);
            ReflectionTestUtils.setField(tx, "id", 7L);

            when(creditTransactionRepository.findByClientIdAndTypeOrderByCreatedAtDesc(
                    eq(100L), eq(CreditTransactionType.PURCHASE), any()))
                    .thenReturn(Collections.singletonList(tx));
            when(userRepository.findById(100L)).thenReturn(Optional.empty());

            List<StripePaymentResponse> result = service.getStripePayments(null);

            assertEquals(1, result.size());
            assertNull(result.get(0).getPaymentIntentId());
            assertEquals(new BigDecimal("25.00"), result.get(0).getAmount());
        }

        @Test
        @DisplayName("Results are sorted by createdAt descending")
        void resultsAreSortedByCreatedAtDescending() {
            setAuth(100L, Role.CLIENT);

            LocalDateTime older  = LocalDateTime.of(2026, 3, 10, 10, 0);
            LocalDateTime newer  = LocalDateTime.of(2026, 3, 20, 10, 0);
            CreditTransaction tx1 = buildPurchaseTx(100L, "pi_001", new BigDecimal("30.00"), older);
            CreditTransaction tx2 = buildPurchaseTx(100L, "pi_002", new BigDecimal("20.00"), newer);

            // Repository returns them in any order; service should sort descending
            when(creditTransactionRepository.findByClientIdAndTypeOrderByCreatedAtDesc(
                    eq(100L), eq(CreditTransactionType.PURCHASE), any()))
                    .thenReturn(Arrays.asList(tx1, tx2));
            when(userRepository.findById(100L)).thenReturn(Optional.empty());

            List<StripePaymentResponse> result = service.getStripePayments(null);

            assertEquals(2, result.size());
            // Newest first
            assertEquals("pi_002", result.get(0).getPaymentIntentId());
            assertEquals("pi_001", result.get(1).getPaymentIntentId());
        }

        @Test
        @DisplayName("OPERATOR can retrieve another client's Stripe payments")
        void operatorCanAccessAnotherClient() {
            setAuth(50L, Role.OPERATOR);

            when(creditTransactionRepository.findByClientIdAndTypeOrderByCreatedAtDesc(
                    eq(200L), eq(CreditTransactionType.PURCHASE), any()))
                    .thenReturn(Collections.emptyList());
            when(userRepository.findById(200L)).thenReturn(Optional.empty());

            List<StripePaymentResponse> result = service.getStripePayments(200L);

            assertNotNull(result);
            verify(creditTransactionRepository).findByClientIdAndTypeOrderByCreatedAtDesc(
                    eq(200L), eq(CreditTransactionType.PURCHASE), any());
        }
    }

    // -------------------------------------------------------------------------
    // Data Isolation: CLIENT A cannot see CLIENT B data
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Data Isolation - CLIENT cannot access other client's data")
    class DataIsolation {

        @Test
        @DisplayName("CLIENT accessing another client's summary throws ForbiddenException")
        void clientCannotAccessOtherClientSummary() {
            setAuth(100L, Role.CLIENT);

            assertThrows(ForbiddenException.class,
                    () -> service.getSummary(999L, FROM, TO));

            verifyNoInteractions(usageEventRepository, creditTransactionRepository);
        }

        @Test
        @DisplayName("CLIENT accessing another client's Twilio metrics throws ForbiddenException")
        void clientCannotAccessOtherClientTwilioMetrics() {
            setAuth(100L, Role.CLIENT);

            assertThrows(ForbiddenException.class,
                    () -> service.getTwilioMetrics(999L, FROM, TO));

            verifyNoInteractions(usageEventRepository);
        }

        @Test
        @DisplayName("CLIENT accessing another client's OpenAI metrics throws ForbiddenException")
        void clientCannotAccessOtherClientOpenAiMetrics() {
            setAuth(100L, Role.CLIENT);

            assertThrows(ForbiddenException.class,
                    () -> service.getOpenAiMetrics(999L, FROM, TO));

            verifyNoInteractions(usageEventRepository);
        }

        @Test
        @DisplayName("CLIENT accessing another client's ElevenLabs metrics throws ForbiddenException")
        void clientCannotAccessOtherClientElevenLabsMetrics() {
            setAuth(100L, Role.CLIENT);

            assertThrows(ForbiddenException.class,
                    () -> service.getElevenLabsMetrics(999L, FROM, TO));

            verifyNoInteractions(usageEventRepository);
        }

        @Test
        @DisplayName("CLIENT accessing another client's Stripe payments throws ForbiddenException")
        void clientCannotAccessOtherClientStripePayments() {
            setAuth(100L, Role.CLIENT);

            assertThrows(ForbiddenException.class,
                    () -> service.getStripePayments(999L));

            verifyNoInteractions(creditTransactionRepository, userRepository);
        }

        @Test
        @DisplayName("OPERATOR can access any client's metrics without throwing")
        void operatorCanAccessAnyClient() {
            setAuth(50L, Role.OPERATOR);

            when(usageEventRepository.sumTwilioCallDurationSeconds(eq(200L), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumTwilioSmsCount(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumOpenAiInputTokens(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumOpenAiOutputTokens(any(), any(), any())).thenReturn(0L);
            when(usageEventRepository.sumElevenLabsCharacters(any(), any(), any())).thenReturn(0L);
            when(creditTransactionRepository.findByClientIdAndType(eq(200L), any()))
                    .thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> service.getSummary(200L, FROM, TO));
        }
    }
}
