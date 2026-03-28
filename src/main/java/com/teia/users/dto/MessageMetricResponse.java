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
 * Response DTO for individual message events in GET /metrics/messages endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Details of a message usage event")
public class MessageMetricResponse {

    @Schema(description = "Event ID", example = "456")
    private Long id;

    @Schema(description = "Session identifier", example = "sess_msg789")
    private String sessionId;

    @Schema(description = "Credits consumed by this message", example = "0.10")
    private BigDecimal creditsUsed;

    @Schema(description = "When the message was sent", example = "2026-03-27T15:45:00")
    private LocalDateTime createdAt;

    @Schema(description = "Current status of the message", example = "COMPLETED")
    private UsageEventStatus status;
}
