package com.teia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teia.users.models.enums.UsageEventStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for individual call events in GET /metrics/calls endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Details of a call usage event")
public class CallMetricResponse {

    @Schema(description = "Event ID", example = "123")
    private Long id;

    @Schema(description = "Session identifier", example = "sess_abc123")
    private String sessionId;

    @Schema(description = "Call duration in seconds", example = "180")
    private Integer durationSeconds;

    @Schema(description = "Credits consumed by this call", example = "2.50")
    private BigDecimal creditsUsed;

    @Schema(description = "When the call was initiated", example = "2026-03-27T14:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Current status of the call", example = "COMPLETED")
    private UsageEventStatus status;
}
