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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for retrieving usage metrics.
 * Handles role-based access control for client data.
 */
@Service
public class MetricsService {

    private final UsageEventRepository usageEventRepository;
    private final UserRepository userRepository;

    public MetricsService(UsageEventRepository usageEventRepository, UserRepository userRepository) {
        this.usageEventRepository = usageEventRepository;
        this.userRepository = userRepository;
    }

    /**
     * Resolves the effective clientId based on the authenticated user's role.
     * - CLIENT role: always uses their own userId, ignores requestedClientId
     * - OPERATOR/ADMIN: uses requestedClientId if provided, otherwise their own userId
     *
     * @param requestedClientId optional clientId from query parameter
     * @return the effective clientId to use for queries
     */
    public Long resolveClientId(Long requestedClientId) {
        JwtUserPrincipal principal = (JwtUserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        if (principal.getRole() == Role.CLIENT) {
            // CLIENT role always sees only their own data
            return principal.getUserId();
        }

        // OPERATOR or ADMIN can view any client's data
        if (requestedClientId != null) {
            return requestedClientId;
        }

        // Default to own data if no clientId specified
        return principal.getUserId();
    }

    /**
     * Get summary metrics for a client.
     */
    public MetricsSummaryResponse getSummary(Long clientId) {
        Long effectiveClientId = resolveClientId(clientId);

        long totalCalls = usageEventRepository.countByClientIdAndType(effectiveClientId, UsageEventType.CALL);
        long totalMessages = usageEventRepository.countByClientIdAndType(effectiveClientId, UsageEventType.MESSAGE);
        BigDecimal totalCreditsUsed = usageEventRepository.sumCreditsUsedByClientId(effectiveClientId);
        long activeSessionsCount = usageEventRepository.countByClientIdAndStatus(effectiveClientId, UsageEventStatus.STARTED);

        BigDecimal currentBalance = BigDecimal.ZERO;
        Optional<TeiaUser> user = userRepository.findById(effectiveClientId);
        if (user.isPresent() && user.get().getCreditBalance() != null) {
            currentBalance = user.get().getCreditBalance();
        }

        return MetricsSummaryResponse.builder()
                .totalCalls(totalCalls)
                .totalMessages(totalMessages)
                .totalCreditsUsed(totalCreditsUsed != null ? totalCreditsUsed : BigDecimal.ZERO)
                .currentBalance(currentBalance)
                .activeSessionsCount(activeSessionsCount)
                .build();
    }

    /**
     * Get call events within a date range.
     */
    public List<CallMetricResponse> getCalls(Long clientId, LocalDate from, LocalDate to) {
        Long effectiveClientId = resolveClientId(clientId);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay(); // exclusive end

        List<UsageEvent> events = usageEventRepository.findCallsByClientIdAndDateRange(
                effectiveClientId, fromDateTime, toDateTime);

        return events.stream()
                .map(this::toCallMetricResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get message events within a date range.
     */
    public List<MessageMetricResponse> getMessages(Long clientId, LocalDate from, LocalDate to) {
        Long effectiveClientId = resolveClientId(clientId);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay(); // exclusive end

        List<UsageEvent> events = usageEventRepository.findMessagesByClientIdAndDateRange(
                effectiveClientId, fromDateTime, toDateTime);

        return events.stream()
                .map(this::toMessageMetricResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get usage over time, aggregated by period (day, week, month).
     * Returns the last 30 days for 'day', 12 weeks for 'week', 12 months for 'month'.
     */
    public UsageOverTimeResponse getUsageOverTime(Long clientId, String period) {
        Long effectiveClientId = resolveClientId(clientId);

        LocalDate now = LocalDate.now();
        LocalDate startDate;

        switch (period.toLowerCase()) {
            case "week":
                startDate = now.minusWeeks(12).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                break;
            case "month":
                startDate = now.minusMonths(12).withDayOfMonth(1);
                break;
            case "day":
            default:
                startDate = now.minusDays(30);
                break;
        }

        LocalDateTime fromDateTime = startDate.atStartOfDay();
        LocalDateTime toDateTime = now.plusDays(1).atStartOfDay();

        List<UsageEvent> events = usageEventRepository.findCompletedEventsByClientIdAndDateRange(
                effectiveClientId, fromDateTime, toDateTime);

        List<UsagePeriodEntry> data = aggregateByPeriod(events, period.toLowerCase(), startDate, now);

        return UsageOverTimeResponse.builder()
                .period(period.toLowerCase())
                .data(data)
                .build();
    }

    private List<UsagePeriodEntry> aggregateByPeriod(List<UsageEvent> events, String period, LocalDate startDate, LocalDate endDate) {
        // Group events by period start date
        Map<LocalDate, BigDecimal> periodTotals = new LinkedHashMap<>();

        // Initialize all periods with zero
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            LocalDate periodStart = getPeriodStart(current, period);
            periodTotals.putIfAbsent(periodStart, BigDecimal.ZERO);
            current = getNextPeriod(current, period);
        }

        // Aggregate credits by period
        for (UsageEvent event : events) {
            if (event.getCreditsUsed() != null) {
                LocalDate eventDate = event.getCreatedAt().toLocalDate();
                LocalDate periodStart = getPeriodStart(eventDate, period);
                periodTotals.merge(periodStart, event.getCreditsUsed(), BigDecimal::add);
            }
        }

        // Convert to list
        return periodTotals.entrySet().stream()
                .map(e -> UsagePeriodEntry.builder()
                        .periodStart(e.getKey())
                        .creditsConsumed(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private LocalDate getPeriodStart(LocalDate date, String period) {
        switch (period) {
            case "week":
                return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month":
                return date.withDayOfMonth(1);
            case "day":
            default:
                return date;
        }
    }

    private LocalDate getNextPeriod(LocalDate date, String period) {
        switch (period) {
            case "week":
                return date.plusWeeks(1);
            case "month":
                return date.plusMonths(1);
            case "day":
            default:
                return date.plusDays(1);
        }
    }

    private CallMetricResponse toCallMetricResponse(UsageEvent event) {
        return CallMetricResponse.builder()
                .id(event.getId())
                .sessionId(event.getSessionId())
                .durationSeconds(event.getDurationSeconds())
                .creditsUsed(event.getCreditsUsed())
                .createdAt(event.getCreatedAt())
                .status(event.getStatus())
                .build();
    }

    private MessageMetricResponse toMessageMetricResponse(UsageEvent event) {
        return MessageMetricResponse.builder()
                .id(event.getId())
                .sessionId(event.getSessionId())
                .creditsUsed(event.getCreditsUsed())
                .createdAt(event.getCreatedAt())
                .status(event.getStatus())
                .build();
    }

    /**
     * Exception thrown when a client is not found.
     */
    public static class ClientNotFoundException extends RuntimeException {
        public ClientNotFoundException(Long clientId) {
            super("Client not found with ID: " + clientId);
        }
    }
}
