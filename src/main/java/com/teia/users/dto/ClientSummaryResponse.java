package com.teia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Summary DTO for listing clients with config status and credit balance.
 * Used by GET /operator/clients endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Summary of a client for listing purposes")
public class ClientSummaryResponse {

    @Schema(description = "Client (user) ID", example = "123")
    private Long clientId;

    @Schema(description = "Client name", example = "Acme Corporation")
    private String name;

    @Schema(description = "Client email address", example = "admin@acme.com")
    private String email;

    @Schema(description = "Whether the client has a configuration record", example = "true")
    private boolean hasConfig;

    @Schema(description = "Client credit balance", example = "150.00")
    private BigDecimal creditBalance;
}
