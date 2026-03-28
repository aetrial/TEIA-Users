package com.teia.users.controllers;

import com.teia.users.dto.AgentPromptContentResponse;
import com.teia.users.dto.CreditCheckResponse;
import com.teia.users.dto.SessionCompleteRequest;
import com.teia.users.dto.SessionCompleteResponse;
import com.teia.users.dto.UsageReportRequest;
import com.teia.users.dto.UsageReportResponse;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.UsageEvent;
import com.teia.users.models.enums.UsageEventStatus;
import com.teia.users.models.enums.UsageEventType;
import com.teia.users.repositories.UserRepository;
import com.teia.users.services.AgentPromptService;
import com.teia.users.services.TwilioWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Internal controller for service-to-service communication.
 * These endpoints are authenticated via InternalRequestFilter (HMAC signing).
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Internal API", description = "Service-to-service endpoints (TEIA Core -> TEIA-users)")
public class InternalSessionController {

    private static final Logger log = LoggerFactory.getLogger(InternalSessionController.class);

    private final TwilioWebhookService webhookService;
    private final UserRepository userRepository;
    private final AgentPromptService agentPromptService;

    public InternalSessionController(TwilioWebhookService webhookService, UserRepository userRepository, AgentPromptService agentPromptService) {
        this.webhookService = webhookService;
        this.userRepository = userRepository;
        this.agentPromptService = agentPromptService;
    }

    /**
     * Session completion callback from TEIA Core.
     * Reconciles credits based on actual usage.
     */
    @PostMapping("/session/complete")
    @Operation(
            summary = "Session completion callback from TEIA Core",
            description = "TEIA Core calls this endpoint when a voice or message session ends. " +
                    "Reconciles credits based on actual usage duration. For calls, credits are calculated " +
                    "per minute (rounded up). For failed sessions, reserved credits are fully refunded. " +
                    "Requires HMAC-SHA256 authentication via X-Internal-Key, X-Timestamp, and X-Client-ID headers."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Session reconciled successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SessionCompleteResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "sessionId": "sess_abc123def456",
                                                "status": "COMPLETED",
                                                "creditsReserved": 5.00,
                                                "creditsUsed": 3.00,
                                                "creditsDifference": 2.00,
                                                "newBalance": 47.00
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request (bad status value)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Invalid status. Must be COMPLETED or FAILED\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or missing HMAC signature",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Unauthorized\", \"message\": \"Invalid signature\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Session not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Session not found\", \"sessionId\": \"sess_unknown\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal error during reconciliation",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Failed to reconcile session\", \"message\": \"Database error\"}")
                    )
            )
    })
    public ResponseEntity<?> completeSession(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Session completion details from TEIA Core",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = SessionCompleteRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "sessionId": "sess_abc123def456",
                                                "clientId": 123,
                                                "type": "CALL",
                                                "actualDurationSeconds": 180,
                                                "status": "COMPLETED"
                                            }"""
                            )
                    )
            )
            @Valid @RequestBody SessionCompleteRequest request) {
        log.info("[InternalSession] Session complete callback: sessionId={}, clientId={}, type={}, duration={}s, status={}",
                request.getSessionId(), request.getClientId(), request.getType(),
                request.getActualDurationSeconds(), request.getStatus());

        // Parse status
        UsageEventStatus status;
        try {
            status = UsageEventStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[InternalSession] Invalid status: {}", request.getStatus());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid status. Must be COMPLETED or FAILED"
            ));
        }

        try {
            // Reconcile the session
            UsageEvent usageEvent = webhookService.reconcileSession(
                    request.getSessionId(),
                    request.getActualDurationSeconds(),
                    status,
                    request.getTwilioCallDurationSeconds(),
                    request.getTwilioSmsCount(),
                    request.getOpenAiInputTokens(),
                    request.getOpenAiOutputTokens(),
                    request.getElevenLabsCharacters()
            );

            // Get updated client balance
            TeiaUser client = userRepository.findById(request.getClientId())
                    .orElse(null);

            BigDecimal newBalance = client != null ? client.getCreditBalance() : BigDecimal.ZERO;
            BigDecimal creditsUsed = usageEvent.getCreditsUsed() != null ? usageEvent.getCreditsUsed() : BigDecimal.ZERO;
            BigDecimal creditsDifference = usageEvent.getCreditsReserved().subtract(creditsUsed);

            SessionCompleteResponse response = SessionCompleteResponse.builder()
                    .sessionId(usageEvent.getSessionId())
                    .status(usageEvent.getStatus().name())
                    .creditsReserved(usageEvent.getCreditsReserved())
                    .creditsUsed(creditsUsed)
                    .creditsDifference(creditsDifference)
                    .newBalance(newBalance)
                    .build();

            log.info("[InternalSession] Session {} reconciled: reserved={}, used={}, diff={}, newBalance={}",
                    request.getSessionId(), usageEvent.getCreditsReserved(), creditsUsed,
                    creditsDifference, newBalance);

            return ResponseEntity.ok(response);

        } catch (TwilioWebhookService.SessionNotFoundException e) {
            log.error("[InternalSession] Session not found: {}", request.getSessionId());
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Session not found",
                    "sessionId", request.getSessionId()
            ));
        } catch (Exception e) {
            log.error("[InternalSession] Error reconciling session: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to reconcile session",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Report usage from TEIA Core.
     * Deducts credits directly without prior reservation.
     */
    @PostMapping("/usage")
    @Operation(
            summary = "Report usage from TEIA Core",
            description = "TEIA Core calls this endpoint after every call/message ends to report usage. " +
                    "Calculates credits to deduct (1 credit/minute for calls rounded up, 1 credit/message), " +
                    "creates UsageEvent + CreditTransaction, and deducts from client balance. " +
                    "Returns 402 if balance would go negative. " +
                    "Requires HMAC-SHA256 authentication via X-Internal-Key, X-Timestamp, and X-Client-ID headers."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usage recorded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UsageReportResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "sessionId": "sess_abc123def456",
                                                "creditsUsed": 3.00,
                                                "newBalance": 47.00
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request (bad type value)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Invalid type. Must be CALL or MESSAGE\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "402",
                    description = "Insufficient credits",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Insufficient credits\", \"message\": \"Balance: 0.00, Required: 3.00\"}")
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Client not found\", \"clientId\": 123}")
                    )
            )
    })
    public ResponseEntity<?> reportUsage(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Usage report from TEIA Core",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UsageReportRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "clientId": 123,
                                                "type": "CALL",
                                                "durationSeconds": 180,
                                                "sessionId": "sess_abc123def456"
                                            }"""
                            )
                    )
            )
            @Valid @RequestBody UsageReportRequest request) {
        log.info("[InternalSession] Usage report: clientId={}, type={}, duration={}s, sessionId={}",
                request.getClientId(), request.getType(), request.getDurationSeconds(), request.getSessionId());

        // Parse type
        UsageEventType type;
        try {
            type = UsageEventType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[InternalSession] Invalid type: {}", request.getType());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid type. Must be CALL or MESSAGE"
            ));
        }

        try {
            UsageEvent usageEvent = webhookService.reportUsage(
                    request.getClientId(),
                    type,
                    request.getDurationSeconds(),
                    request.getSessionId()
            );

            // Get updated client balance
            TeiaUser client = userRepository.findById(request.getClientId()).orElse(null);
            BigDecimal newBalance = client != null ? client.getCreditBalance() : BigDecimal.ZERO;

            UsageReportResponse response = UsageReportResponse.builder()
                    .sessionId(usageEvent.getSessionId())
                    .creditsUsed(usageEvent.getCreditsUsed())
                    .newBalance(newBalance)
                    .build();

            log.info("[InternalSession] Usage reported: sessionId={}, creditsUsed={}, newBalance={}",
                    usageEvent.getSessionId(), usageEvent.getCreditsUsed(), newBalance);

            return ResponseEntity.ok(response);

        } catch (TwilioWebhookService.InsufficientCreditsException e) {
            log.warn("[InternalSession] Insufficient credits for client {}: {}", request.getClientId(), e.getMessage());
            return ResponseEntity.status(402).body(Map.of(
                    "error", "Insufficient credits",
                    "message", e.getMessage()
            ));
        } catch (TwilioWebhookService.ClientNotFoundException e) {
            log.error("[InternalSession] Client not found: {}", request.getClientId());
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Client not found",
                    "clientId", request.getClientId()
            ));
        } catch (Exception e) {
            log.error("[InternalSession] Error reporting usage: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to report usage",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Check client credits.
     * Returns credit balance and whether the client has credits.
     */
    @GetMapping("/clients/{clientId}/credits")
    @Operation(
            summary = "Check client credits",
            description = "TEIA Core calls this endpoint to check if a client has credits before starting a session. " +
                    "Requires HMAC-SHA256 authentication via X-Internal-Key, X-Timestamp, and X-Client-ID headers."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Credit check successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreditCheckResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "clientId": 123,
                                                "creditBalance": 50.00,
                                                "hasCredits": true
                                            }"""
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Client not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Client not found\", \"clientId\": 123}")
                    )
            )
    })
    public ResponseEntity<?> checkCredits(
            @Parameter(description = "Client ID", required = true, example = "123")
            @PathVariable Long clientId) {
        log.info("[InternalSession] Credit check for client: {}", clientId);

        return userRepository.findById(clientId)
                .<ResponseEntity<?>>map(client -> {
                    BigDecimal balance = client.getCreditBalance() != null ? client.getCreditBalance() : BigDecimal.ZERO;
                    boolean hasCredits = balance.compareTo(BigDecimal.ONE) >= 0;

                    CreditCheckResponse response = CreditCheckResponse.builder()
                            .clientId(clientId)
                            .creditBalance(balance)
                            .hasCredits(hasCredits)
                            .build();

                    log.info("[InternalSession] Credit check: clientId={}, balance={}, hasCredits={}",
                            clientId, balance, hasCredits);

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    log.warn("[InternalSession] Client not found for credit check: {}", clientId);
                    return ResponseEntity.status(404).body(Map.of(
                            "error", "Client not found",
                            "clientId", clientId
                    ));
                });
    }

    /**
     * Get the active agent prompt for a client.
     */
    @GetMapping("/clients/{clientId}/prompt")
    @Operation(
            summary = "Get active agent prompt for a client",
            description = "TEIA Core calls this endpoint to retrieve the active system prompt for a client. " +
                    "Requires HMAC-SHA256 authentication via X-Internal-Key, X-Timestamp, and X-Client-ID headers."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Active prompt returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AgentPromptContentResponse.class),
                            examples = @ExampleObject(
                                    value = "{\"content\": \"You are a helpful assistant...\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No active prompt found for client",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"No active prompt found\", \"clientId\": 123}")
                    )
            )
    })
    public ResponseEntity<?> getClientPrompt(
            @Parameter(description = "Client ID", required = true, example = "123")
            @PathVariable Long clientId) {
        log.info("[InternalSession] Prompt fetch for client: {}", clientId);

        return agentPromptService.getActivePromptContent(clientId)
                .<ResponseEntity<?>>map(content -> {
                    log.info("[InternalSession] Active prompt found for client: {}", clientId);
                    return ResponseEntity.ok(AgentPromptContentResponse.builder().content(content).build());
                })
                .orElseGet(() -> {
                    log.warn("[InternalSession] No active prompt for client: {}", clientId);
                    return ResponseEntity.status(404).body(Map.of(
                            "error", "No active prompt found",
                            "clientId", clientId
                    ));
                });
    }

    /**
     * Health check for internal connectivity.
     */
    @GetMapping("/health")
    @Operation(
            summary = "Internal health check",
            description = "Verifies that the internal API is reachable. " +
                    "Used by TEIA Core to verify connectivity before sending session callbacks."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Service is healthy",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = "{\"status\": \"ok\", \"service\": \"teia-users\", \"endpoint\": \"internal\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or missing HMAC signature",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "teia-users",
                "endpoint", "internal"
        ));
    }
}
