package com.teia.users.controllers;

import com.teia.users.dto.ClientConfigRequest;
import com.teia.users.dto.ClientConfigResponse;
import com.teia.users.dto.ClientSummaryResponse;
import com.teia.users.dto.ErrorResponse;
import com.teia.users.services.ClientConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Operator-only endpoints for managing client configurations.
 * All endpoints require OPERATOR or ADMIN role.
 */
@RestController
@RequestMapping("/operator")
@Tag(name = "Operator - Client Config", description = "Manage client configurations (OPERATOR and ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class OperatorClientController {

    private final ClientConfigService clientConfigService;

    public OperatorClientController(ClientConfigService clientConfigService) {
        this.clientConfigService = clientConfigService;
    }

    @GetMapping("/clients")
    @Operation(summary = "List all clients with config status and credit balance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of clients retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied - OPERATOR or ADMIN role required",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ClientSummaryResponse>> listClients() {
        List<ClientSummaryResponse> clients = clientConfigService.listClientsWithConfigStatus();
        return ResponseEntity.ok(clients);
    }

    @PostMapping("/clients/{clientId}/config")
    @Operation(summary = "Create or overwrite full client configuration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration created/updated successfully"),
        @ApiResponse(responseCode = "404", description = "Client not found",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClientConfigResponse> createOrUpdateConfig(
            @Parameter(description = "Client ID") @PathVariable Long clientId,
            @RequestBody ClientConfigRequest request) {
        ClientConfigResponse response = clientConfigService.createOrUpdateConfig(clientId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/clients/{clientId}/config")
    @Operation(summary = "Get client configuration (sensitive fields masked)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Configuration not found for client",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClientConfigResponse> getConfig(
            @Parameter(description = "Client ID") @PathVariable Long clientId) {
        ClientConfigResponse response = clientConfigService.getConfig(clientId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/clients/{clientId}/config")
    @Operation(summary = "Partially update client configuration (only provided fields)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Configuration updated successfully"),
        @ApiResponse(responseCode = "404", description = "Configuration not found for client",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Access denied",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClientConfigResponse> patchConfig(
            @Parameter(description = "Client ID") @PathVariable Long clientId,
            @RequestBody ClientConfigRequest request) {
        ClientConfigResponse response = clientConfigService.patchConfig(clientId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Exception handler for ClientNotFoundException
     */
    @ExceptionHandler(ClientConfigService.ClientNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleClientNotFound(ClientConfigService.ClientNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(404, "Not Found", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Exception handler for ConfigNotFoundException
     */
    @ExceptionHandler(ClientConfigService.ConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConfigNotFound(ClientConfigService.ConfigNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(404, "Not Found", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
