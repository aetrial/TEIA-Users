package com.teia.users.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for session completion callback from TEIA Core.
 */
@Data
public class SessionCompleteRequest {

    @NotNull(message = "Client ID is required")
    private Long clientId;

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "Type is required")
    private String type; // CALL or MESSAGE

    private Integer actualDurationSeconds; // null for messages

    @NotBlank(message = "Status is required")
    private String status; // COMPLETED or FAILED
}
