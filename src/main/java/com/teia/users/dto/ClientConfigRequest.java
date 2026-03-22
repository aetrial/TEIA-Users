package com.teia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating a client's configuration.
 * All fields are optional for PATCH operations; required fields should be validated at service layer for POST.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request payload for creating or updating client configuration")
public class ClientConfigRequest {

    // Twilio
    @Schema(description = "Twilio account SID", example = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
    private String twilioAccountSid;

    @Schema(description = "Twilio auth token (stored encrypted)", example = "your-auth-token")
    private String twilioAuthToken;

    @Schema(description = "Twilio phone number for voice calls", example = "+15551234567")
    private String twilioCallNumber;

    @Schema(description = "Twilio phone number for WhatsApp", example = "+15559876543")
    private String twilioWhatsappNumber;

    // ElevenLabs
    @Schema(description = "ElevenLabs API key (stored encrypted)", example = "xi_...")
    private String elevenLabsApiKey;

    @Schema(description = "ElevenLabs voice agent ID", example = "agent_abc123")
    private String elevenLabsAgentId;

    // OpenAI
    @Schema(description = "OpenAI API key (stored encrypted)", example = "sk-...")
    private String openAiApiKey;

    @Schema(description = "OpenAI assistant ID", example = "asst_abc123")
    private String openAiAssistantId;

    // Cal.com
    @Schema(description = "Cal.com API key (stored encrypted)", example = "cal_...")
    private String calApiKey;

    @Schema(description = "Cal.com user ID", example = "12345")
    private String calUserId;

    @Schema(description = "Cal.com event type ID", example = "67890")
    private String calEventId;
}
