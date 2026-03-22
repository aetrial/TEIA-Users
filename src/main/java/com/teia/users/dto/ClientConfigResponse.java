package com.teia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for client configuration.
 * Sensitive fields are masked (showing only last 4 characters or "****").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Client configuration response with sensitive fields masked")
public class ClientConfigResponse {

    @Schema(description = "Configuration ID", example = "1")
    private Long id;

    @Schema(description = "Client (user) ID", example = "123")
    private Long clientId;

    // Twilio (authToken masked)
    @Schema(description = "Twilio account SID", example = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
    private String twilioAccountSid;

    @Schema(description = "Twilio auth token (masked for security)", example = "****abcd")
    private String twilioAuthToken;

    @Schema(description = "Twilio phone number for voice calls", example = "+15551234567")
    private String twilioCallNumber;

    @Schema(description = "Twilio phone number for WhatsApp", example = "+15559876543")
    private String twilioWhatsappNumber;

    // ElevenLabs (apiKey masked)
    @Schema(description = "ElevenLabs API key (masked for security)", example = "****xyz1")
    private String elevenLabsApiKey;

    @Schema(description = "ElevenLabs voice agent ID", example = "agent_abc123")
    private String elevenLabsAgentId;

    // OpenAI (apiKey masked)
    @Schema(description = "OpenAI API key (masked for security)", example = "****sk12")
    private String openAiApiKey;

    @Schema(description = "OpenAI assistant ID", example = "asst_abc123")
    private String openAiAssistantId;

    // Cal.com (apiKey masked)
    @Schema(description = "Cal.com API key (masked for security)", example = "****cal1")
    private String calApiKey;

    @Schema(description = "Cal.com user ID", example = "12345")
    private String calUserId;

    @Schema(description = "Cal.com event type ID", example = "67890")
    private String calEventId;

    @Schema(description = "Last update timestamp", example = "2026-03-21T10:30:00")
    private LocalDateTime updatedAt;
}
