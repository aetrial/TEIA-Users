package com.teia.users.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for usage report from TEIA Core.
 * Used when TEIA Core reports usage after a call/message ends (no prior reservation).
 */
@Data
public class UsageReportRequest {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotBlank(message = "Type is required (CALL or MESSAGE)")
    private String type; // CALL or MESSAGE

    private Integer durationSeconds; // For calls - null for messages

    @NotBlank(message = "Session ID is required")
    private String sessionId;
}
