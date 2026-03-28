package com.teia.users.controllers;

import com.teia.users.dto.AgentPromptRequest;
import com.teia.users.dto.AgentPromptResponse;
import com.teia.users.dto.AgentPromptSummaryResponse;
import com.teia.users.security.JwtUserPrincipal;
import com.teia.users.services.AgentPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for managing agent prompts.
 * All endpoints are scoped to the authenticated client's data.
 */
@RestController
@RequestMapping("/prompts")
@Tag(name = "Agent Prompts", description = "Manage AI agent system prompts")
public class AgentPromptController {

    private static final Logger log = LoggerFactory.getLogger(AgentPromptController.class);

    private final AgentPromptService agentPromptService;

    public AgentPromptController(AgentPromptService agentPromptService) {
        this.agentPromptService = agentPromptService;
    }

    /**
     * Get the currently active prompt for the authenticated client.
     */
    @GetMapping("/active")
    @Operation(
            summary = "Get active prompt",
            description = "Returns the currently active system prompt for the authenticated client."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Active prompt found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AgentPromptResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No active prompt exists",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"No active prompt found\"}")
                    )
            )
    })
    public ResponseEntity<?> getActivePrompt(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        Long clientId = principal.getUserId();
        log.info("[AgentPromptController] Getting active prompt for client: {}", clientId);

        return agentPromptService.getActivePrompt(clientId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "error", "No active prompt found"
                )));
    }

    /**
     * List all prompt versions for the authenticated client.
     */
    @GetMapping
    @Operation(
            summary = "List all prompts",
            description = "Returns all prompt versions for the authenticated client, " +
                    "ordered by version descending. Content is truncated to first 100 characters."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "List of prompt summaries",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AgentPromptSummaryResponse.class)
                    )
            )
    })
    public ResponseEntity<List<AgentPromptSummaryResponse>> listPrompts(
            @AuthenticationPrincipal JwtUserPrincipal principal) {
        Long clientId = principal.getUserId();
        log.info("[AgentPromptController] Listing prompts for client: {}", clientId);

        List<AgentPromptSummaryResponse> prompts = agentPromptService.listPrompts(clientId);
        return ResponseEntity.ok(prompts);
    }

    /**
     * Create a new prompt version.
     * Automatically increments version and sets as active.
     */
    @PostMapping
    @Operation(
            summary = "Create new prompt",
            description = "Creates a new prompt version. The version number is auto-incremented. " +
                    "The new prompt becomes active and all previous prompts are deactivated."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Prompt created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AgentPromptResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Validation failed\", \"details\": {\"content\": \"Content cannot be empty\"}}")
                    )
            )
    })
    public ResponseEntity<AgentPromptResponse> createPrompt(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @Valid @RequestBody AgentPromptRequest request) {
        Long clientId = principal.getUserId();
        log.info("[AgentPromptController] Creating prompt for client: {}", clientId);

        AgentPromptResponse created = agentPromptService.createPrompt(clientId, request);
        return ResponseEntity.ok(created);
    }

    /**
     * Activate a previous prompt version (rollback).
     */
    @PostMapping("/{id}/activate")
    @Operation(
            summary = "Activate a prompt version",
            description = "Activates a specific prompt version by ID. This can be used to roll back " +
                    "to a previous version. All other prompts are deactivated."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Prompt activated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AgentPromptResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Prompt not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"error\": \"Prompt not found\"}")
                    )
            )
    })
    public ResponseEntity<?> activatePrompt(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @Parameter(description = "Prompt ID to activate", required = true)
            @PathVariable Long id) {
        Long clientId = principal.getUserId();
        log.info("[AgentPromptController] Activating prompt id={} for client: {}", id, clientId);

        return agentPromptService.activatePrompt(clientId, id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "error", "Prompt not found"
                )));
    }
}
