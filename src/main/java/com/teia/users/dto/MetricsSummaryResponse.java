package com.teia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for GET /metrics/summary endpoint.
 * Contains aggregated usage metrics for a client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Summary of usage metrics for a client")
public class MetricsSummaryResponse {

    @Schema(description = "Total number of call events", example = "150")
    private long totalCalls;

    @Schema(description = "Total number of message events", example = "320")
    private long totalMessages;

    @Schema(description = "Total credits consumed across all completed events", example = "45.50")
    private BigDecimal totalCreditsUsed;

    @Schema(description = "Current credit balance for the client", example = "100.00")
    private BigDecimal currentBalance;

    @Schema(description = "Number of currently active sessions (status = STARTED)", example = "3")
    private long activeSessionsCount;
}
