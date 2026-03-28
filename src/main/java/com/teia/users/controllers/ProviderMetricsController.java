package com.teia.users.controllers;

import com.teia.users.dto.*;
import com.teia.users.services.ProviderMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for provider-level consumption metrics (Twilio, OpenAI, ElevenLabs, Stripe).
 * All endpoints require authentication.
 * CLIENT role sees only their own data; attempting to access another client's data returns 403.
 * OPERATOR/ADMIN can view any client's data via the clientId query parameter.
 */
@RestController
@RequestMapping("/metrics/providers")
@Tag(name = "Provider Metrics", description = "Provider consumption metrics: Twilio, OpenAI, ElevenLabs, Stripe")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
public class ProviderMetricsController {

    private final ProviderMetricsService providerMetricsService;

    public ProviderMetricsController(ProviderMetricsService providerMetricsService) {
        this.providerMetricsService = providerMetricsService;
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Provider consumption summary",
            description = "Returns aggregated totals across all providers for the given date range. " +
                    "CLIENT role sees only their own data. OPERATOR/ADMIN can pass clientId to view any client.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> getSummary(
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

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        return ResponseEntity.ok(providerMetricsService.getSummary(clientId, fromDt, toDt));
    }

    @GetMapping("/twilio")
    @Operation(
            summary = "Twilio consumption metrics",
            description = "Returns per-session Twilio telemetry (call duration, SMS count) and aggregated totals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Twilio metrics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> getTwilioMetrics(
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

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        return ResponseEntity.ok(providerMetricsService.getTwilioMetrics(clientId, fromDt, toDt));
    }

    @GetMapping("/openai")
    @Operation(
            summary = "OpenAI consumption metrics",
            description = "Returns per-session OpenAI token usage (input + output) and aggregated totals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OpenAI metrics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> getOpenAiMetrics(
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

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        return ResponseEntity.ok(providerMetricsService.getOpenAiMetrics(clientId, fromDt, toDt));
    }

    @GetMapping("/elevenlabs")
    @Operation(
            summary = "ElevenLabs consumption metrics",
            description = "Returns per-session ElevenLabs character usage and aggregated totals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ElevenLabs metrics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> getElevenLabsMetrics(
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

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();

        return ResponseEntity.ok(providerMetricsService.getElevenLabsMetrics(clientId, fromDt, toDt));
    }

    @GetMapping("/stripe")
    @Operation(
            summary = "Stripe payment history",
            description = "Returns the last 20 Stripe payments, merging local CreditTransactions with live Stripe PaymentIntents. " +
                    "Requires stripeCustomerId to be set on the user for live Stripe data.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stripe payments retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<StripePaymentResponse>> getStripePayments(
            @Parameter(description = "Client ID (OPERATOR/ADMIN only)")
            @RequestParam(required = false) Long clientId) {

        return ResponseEntity.ok(providerMetricsService.getStripePayments(clientId));
    }
}
