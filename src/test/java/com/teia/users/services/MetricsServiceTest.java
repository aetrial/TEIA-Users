package com.teia.users.services;

import com.teia.users.dto.*;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.UsageEvent;
import com.teia.users.models.enums.Role;
import com.teia.users.models.enums.UsageEventStatus;
import com.teia.users.models.enums.UsageEventType;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService")
class MetricsServiceTest {

    @Mock
    private UsageEventRepository usageEventRepository;

    @Mock
    private UserRepository userRepository;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService(usageEventRepository, userRepository);
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContextWithPrincipal(Long userId, Role role) {
        JwtUserPrincipal principal = new JwtUserPrincipal(userId, "test@example.com", role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("resolveClientId - Data Isolation Logic")
    class ResolveClientId {

        @Test
        @DisplayName("CLIENT role always uses own userId, ignores requestedClientId")
        void clientRoleUsesOwnUserId() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            // Even if clientId 999 is requested, CLIENT gets their own ID
            Long resolved = metricsService.resolveClientId(999L);
            assertEquals(100L, resolved);

            // Same when null is passed
            Long resolvedNull = metricsService.resolveClientId(null);
            assertEquals(100L, resolvedNull);
        }

        @Test
        @DisplayName("OPERATOR role uses requestedClientId when provided")
        void operatorRoleUsesRequestedClientId() {
            setSecurityContextWithPrincipal(50L, Role.OPERATOR);

            Long resolved = metricsService.resolveClientId(200L);
            assertEquals(200L, resolved);
        }

        @Test
        @DisplayName("OPERATOR role uses own userId when requestedClientId is null")
        void operatorRoleUsesOwnUserIdWhenNull() {
            setSecurityContextWithPrincipal(50L, Role.OPERATOR);

            Long resolved = metricsService.resolveClientId(null);
            assertEquals(50L, resolved);
        }

        @Test
        @DisplayName("ADMIN role uses requestedClientId when provided")
        void adminRoleUsesRequestedClientId() {
            setSecurityContextWithPrincipal(1L, Role.ADMIN);

            Long resolved = metricsService.resolveClientId(300L);
            assertEquals(300L, resolved);
        }

        @Test
        @DisplayName("ADMIN role uses own userId when requestedClientId is null")
        void adminRoleUsesOwnUserIdWhenNull() {
            setSecurityContextWithPrincipal(1L, Role.ADMIN);

            Long resolved = metricsService.resolveClientId(null);
            assertEquals(1L, resolved);
        }
    }

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("Returns aggregated metrics for a client")
        void returnsAggregatedMetrics() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            when(usageEventRepository.countByClientIdAndType(100L, UsageEventType.CALL)).thenReturn(25L);
            when(usageEventRepository.countByClientIdAndType(100L, UsageEventType.MESSAGE)).thenReturn(50L);
            when(usageEventRepository.sumCreditsUsedByClientId(100L)).thenReturn(new BigDecimal("30.00"));
            when(usageEventRepository.countByClientIdAndStatus(100L, UsageEventStatus.STARTED)).thenReturn(2L);

            TeiaUser user = new TeiaUser();
            user.setCreditBalance(new BigDecimal("70.00"));
            when(userRepository.findById(100L)).thenReturn(Optional.of(user));

            MetricsSummaryResponse response = metricsService.getSummary(null);

            assertEquals(25L, response.getTotalCalls());
            assertEquals(50L, response.getTotalMessages());
            assertEquals(new BigDecimal("30.00"), response.getTotalCreditsUsed());
            assertEquals(new BigDecimal("70.00"), response.getCurrentBalance());
            assertEquals(2L, response.getActiveSessionsCount());
        }

        @Test
        @DisplayName("Returns zero balance when user not found")
        void returnsZeroBalanceWhenUserNotFound() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            when(usageEventRepository.countByClientIdAndType(100L, UsageEventType.CALL)).thenReturn(0L);
            when(usageEventRepository.countByClientIdAndType(100L, UsageEventType.MESSAGE)).thenReturn(0L);
            when(usageEventRepository.sumCreditsUsedByClientId(100L)).thenReturn(BigDecimal.ZERO);
            when(usageEventRepository.countByClientIdAndStatus(100L, UsageEventStatus.STARTED)).thenReturn(0L);
            when(userRepository.findById(100L)).thenReturn(Optional.empty());

            MetricsSummaryResponse response = metricsService.getSummary(null);

            assertEquals(BigDecimal.ZERO, response.getCurrentBalance());
        }

        @Test
        @DisplayName("Handles null creditsUsed sum gracefully")
        void handlesNullCreditsUsedSum() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            when(usageEventRepository.countByClientIdAndType(100L, UsageEventType.CALL)).thenReturn(0L);
            when(usageEventRepository.countByClientIdAndType(100L, UsageEventType.MESSAGE)).thenReturn(0L);
            when(usageEventRepository.sumCreditsUsedByClientId(100L)).thenReturn(null);
            when(usageEventRepository.countByClientIdAndStatus(100L, UsageEventStatus.STARTED)).thenReturn(0L);
            when(userRepository.findById(100L)).thenReturn(Optional.empty());

            MetricsSummaryResponse response = metricsService.getSummary(null);

            assertEquals(BigDecimal.ZERO, response.getTotalCreditsUsed());
        }
    }

    @Nested
    @DisplayName("getCalls")
    class GetCalls {

        @Test
        @DisplayName("Returns calls filtered by date range")
        void returnsCallsFilteredByDateRange() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            UsageEvent event1 = new UsageEvent();
            event1.setId(1L);
            event1.setSessionId("sess_001");
            event1.setDurationSeconds(120);
            event1.setCreditsUsed(new BigDecimal("2.00"));
            event1.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
            event1.setStatus(UsageEventStatus.COMPLETED);

            UsageEvent event2 = new UsageEvent();
            event2.setId(2L);
            event2.setSessionId("sess_002");
            event2.setDurationSeconds(60);
            event2.setCreditsUsed(new BigDecimal("1.00"));
            event2.setCreatedAt(LocalDateTime.of(2026, 3, 20, 14, 0));
            event2.setStatus(UsageEventStatus.COMPLETED);

            when(usageEventRepository.findCallsByClientIdAndDateRange(
                    eq(100L),
                    eq(LocalDate.of(2026, 3, 1).atStartOfDay()),
                    eq(LocalDate.of(2026, 4, 1).atStartOfDay())
            )).thenReturn(Arrays.asList(event1, event2));

            List<CallMetricResponse> response = metricsService.getCalls(
                    null,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31)
            );

            assertEquals(2, response.size());
            assertEquals("sess_001", response.get(0).getSessionId());
            assertEquals(Integer.valueOf(120), response.get(0).getDurationSeconds());
            assertEquals("sess_002", response.get(1).getSessionId());
        }

        @Test
        @DisplayName("Returns empty list when no calls in range")
        void returnsEmptyListWhenNoCallsInRange() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            when(usageEventRepository.findCallsByClientIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            List<CallMetricResponse> response = metricsService.getCalls(
                    null,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31)
            );

            assertTrue(response.isEmpty());
        }
    }

    @Nested
    @DisplayName("getMessages")
    class GetMessages {

        @Test
        @DisplayName("Returns messages filtered by date range")
        void returnsMessagesFilteredByDateRange() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            UsageEvent event = new UsageEvent();
            event.setId(10L);
            event.setSessionId("msg_001");
            event.setCreditsUsed(new BigDecimal("0.10"));
            event.setCreatedAt(LocalDateTime.of(2026, 3, 15, 9, 0));
            event.setStatus(UsageEventStatus.COMPLETED);

            when(usageEventRepository.findMessagesByClientIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.singletonList(event));

            List<MessageMetricResponse> response = metricsService.getMessages(
                    null,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31)
            );

            assertEquals(1, response.size());
            assertEquals("msg_001", response.get(0).getSessionId());
            assertEquals(new BigDecimal("0.10"), response.get(0).getCreditsUsed());
        }
    }

    @Nested
    @DisplayName("getUsageOverTime")
    class GetUsageOverTime {

        @Test
        @DisplayName("Aggregates usage by day")
        void aggregatesUsageByDay() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            UsageEvent event1 = new UsageEvent();
            event1.setCreditsUsed(new BigDecimal("5.00"));
            event1.setCreatedAt(LocalDateTime.of(2026, 3, 25, 10, 0));

            UsageEvent event2 = new UsageEvent();
            event2.setCreditsUsed(new BigDecimal("3.00"));
            event2.setCreatedAt(LocalDateTime.of(2026, 3, 25, 14, 0));

            UsageEvent event3 = new UsageEvent();
            event3.setCreditsUsed(new BigDecimal("2.00"));
            event3.setCreatedAt(LocalDateTime.of(2026, 3, 26, 9, 0));

            when(usageEventRepository.findCompletedEventsByClientIdAndDateRange(any(), any(), any()))
                    .thenReturn(Arrays.asList(event1, event2, event3));

            UsageOverTimeResponse response = metricsService.getUsageOverTime(null, "day");

            assertEquals("day", response.getPeriod());
            assertNotNull(response.getData());
            assertFalse(response.getData().isEmpty());

            // Find entries for our test dates
            Optional<UsagePeriodEntry> march25 = response.getData().stream()
                    .filter(e -> e.getPeriodStart().equals(LocalDate.of(2026, 3, 25)))
                    .findFirst();
            Optional<UsagePeriodEntry> march26 = response.getData().stream()
                    .filter(e -> e.getPeriodStart().equals(LocalDate.of(2026, 3, 26)))
                    .findFirst();

            assertTrue(march25.isPresent());
            assertEquals(new BigDecimal("8.00"), march25.get().getCreditsConsumed());

            assertTrue(march26.isPresent());
            assertEquals(new BigDecimal("2.00"), march26.get().getCreditsConsumed());
        }

        @Test
        @DisplayName("Handles events with null creditsUsed")
        void handlesNullCreditsUsed() {
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            UsageEvent event = new UsageEvent();
            event.setCreditsUsed(null); // null credits
            event.setCreatedAt(LocalDateTime.of(2026, 3, 25, 10, 0));

            when(usageEventRepository.findCompletedEventsByClientIdAndDateRange(any(), any(), any()))
                    .thenReturn(Collections.singletonList(event));

            // Should not throw exception
            UsageOverTimeResponse response = metricsService.getUsageOverTime(null, "day");

            assertNotNull(response);
            assertEquals("day", response.getPeriod());
        }
    }

    @Nested
    @DisplayName("Client A cannot see Client B's data")
    class DataIsolation {

        @Test
        @DisplayName("CLIENT role user cannot access another client's summary")
        void clientCannotAccessOtherClientSummary() {
            // User 100 (CLIENT) is authenticated
            setSecurityContextWithPrincipal(100L, Role.CLIENT);

            // Setup mocks for user 100's data
            when(usageEventRepository.countByClientIdAndType(100L, UsageEventType.CALL)).thenReturn(10L);
            when(usageEventRepository.countByClientIdAndType(100L, UsageEventType.MESSAGE)).thenReturn(20L);
            when(usageEventRepository.sumCreditsUsedByClientId(100L)).thenReturn(new BigDecimal("15.00"));
            when(usageEventRepository.countByClientIdAndStatus(100L, UsageEventStatus.STARTED)).thenReturn(1L);
            when(userRepository.findById(100L)).thenReturn(Optional.empty());

            // Even when requesting clientId=200, the service should use user 100's data
            MetricsSummaryResponse response = metricsService.getSummary(200L);

            // Verify repository was called with user 100's ID, not 200
            verify(usageEventRepository).countByClientIdAndType(100L, UsageEventType.CALL);
            verify(usageEventRepository).countByClientIdAndType(100L, UsageEventType.MESSAGE);
            verify(usageEventRepository, never()).countByClientIdAndType(eq(200L), any());
        }

        @Test
        @DisplayName("OPERATOR can access another client's summary")
        void operatorCanAccessOtherClientSummary() {
            // User 50 (OPERATOR) is authenticated
            setSecurityContextWithPrincipal(50L, Role.OPERATOR);

            // Setup mocks for user 200's data
            when(usageEventRepository.countByClientIdAndType(200L, UsageEventType.CALL)).thenReturn(100L);
            when(usageEventRepository.countByClientIdAndType(200L, UsageEventType.MESSAGE)).thenReturn(200L);
            when(usageEventRepository.sumCreditsUsedByClientId(200L)).thenReturn(new BigDecimal("150.00"));
            when(usageEventRepository.countByClientIdAndStatus(200L, UsageEventStatus.STARTED)).thenReturn(5L);
            when(userRepository.findById(200L)).thenReturn(Optional.empty());

            // OPERATOR requests clientId=200
            MetricsSummaryResponse response = metricsService.getSummary(200L);

            // Verify repository was called with requested client 200's ID
            verify(usageEventRepository).countByClientIdAndType(200L, UsageEventType.CALL);
            verify(usageEventRepository).countByClientIdAndType(200L, UsageEventType.MESSAGE);
            assertEquals(100L, response.getTotalCalls());
            assertEquals(200L, response.getTotalMessages());
        }
    }
}
