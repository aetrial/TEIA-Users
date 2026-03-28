package com.teia.users.controllers;

import com.teia.users.dto.*;
import com.teia.users.services.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for usage metrics endpoints.
 * All endpoints require authentication.
 * CLIENT role sees only their own data.
 * OPERATOR/ADMIN can view any client's data via clientId query param.
 */
@RestController
@RequestMapping("/metrics")
@Tag(name = "Metrics", description = "Usage metrics and analytics")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get usage summary metrics",
            description = "Returns total calls, total messages, total credits used, current balance, and active sessions count. " +
                    "CLIENT role sees only their own data. OPERATOR/ADMIN can specify clientId to view any client's data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MetricsSummaryResponse> getSummary(
            @Parameter(description = "Client ID (OPERATOR/ADMIN only, ignored for CLIENT role)")
            @RequestParam(required = false) Long clientId) {
        MetricsSummaryResponse response = metricsService.getSummary(clientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/calls")
    @Operation(summary = "Get call events within date range",
            description = "Returns list of call events with duration, credits used, timestamp, and status. " +
                    "Both 'from' and 'to' dates are required and inclusive.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Calls retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or missing parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> getCalls(
            @Parameter(description = "Start date (inclusive)", required = true, example = "2026-03-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (inclusive)", required = true, example = "2026-03-27")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Client ID (OPERATOR/ADMIN only)")
            @RequestParam(required = false) Long clientId) {

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(400, "Bad Request", "'from' date must not be after 'to' date"));
        }

        List<CallMetricResponse> response = metricsService.getCalls(clientId, from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/messages")
    @Operation(summary = "Get message events within date range",
            description = "Returns list of message events with credits used, timestamp, and status. " +
                    "Both 'from' and 'to' dates are required and inclusive.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Messages retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or missing parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> getMessages(
            @Parameter(description = "Start date (inclusive)", required = true, example = "2026-03-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (inclusive)", required = true, example = "2026-03-27")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Client ID (OPERATOR/ADMIN only)")
            @RequestParam(required = false) Long clientId) {

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(400, "Bad Request", "'from' date must not be after 'to' date"));
        }

        List<MessageMetricResponse> response = metricsService.getMessages(clientId, from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/usage-over-time")
    @Operation(summary = "Get usage over time aggregated by period",
            description = "Returns credits consumed grouped by day, week, or month. " +
                    "Returns last 30 days for 'day', 12 weeks for 'week', 12 months for 'month'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usage data retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid period parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> getUsageOverTime(
            @Parameter(description = "Aggregation period", required = true, example = "day",
                    schema = @Schema(allowableValues = {"day", "week", "month"}))
            @RequestParam String period,
            @Parameter(description = "Client ID (OPERATOR/ADMIN only)")
            @RequestParam(required = false) Long clientId) {

        String normalizedPeriod = period.toLowerCase();
        if (!normalizedPeriod.equals("day") && !normalizedPeriod.equals("week") && !normalizedPeriod.equals("month")) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(400, "Bad Request", "Period must be one of: day, week, month"));
        }

        UsageOverTimeResponse response = metricsService.getUsageOverTime(clientId, normalizedPeriod);
        return ResponseEntity.ok(response);
    }
}
