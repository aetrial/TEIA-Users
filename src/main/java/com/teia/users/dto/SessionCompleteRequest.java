package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    // Provider telemetry — all nullable
    @Schema(description = "Twilio call duration in seconds", example = "300")
    private Integer twilioCallDurationSeconds;

    @Schema(description = "Number of SMS messages sent via Twilio", example = "2")
    private Integer twilioSmsCount;

    @Schema(description = "OpenAI input tokens consumed", example = "1500")
    private Integer openAiInputTokens;

    @Schema(description = "OpenAI output tokens generated", example = "800")
    private Integer openAiOutputTokens;

    @Schema(description = "ElevenLabs characters synthesized", example = "5000")
    private Integer elevenLabsCharacters;
}
