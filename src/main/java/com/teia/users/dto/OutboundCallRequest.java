package com.teia.users.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for initiating an outbound call.
 */
@Data
public class OutboundCallRequest {

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Destination phone number is required")
    private String to;

    // Optional: custom prompt or configuration for this call
    private String customPrompt;

    // Optional: webhook URL for status callbacks
    private String statusCallbackUrl;
}
