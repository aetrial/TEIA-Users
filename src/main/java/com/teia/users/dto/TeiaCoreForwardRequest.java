package com.teia.users.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for forwarding webhook data to TEIA Core.
 * Contains the original Twilio parameters plus decrypted client credentials.
 */
@Data
@Builder
public class TeiaCoreForwardRequest {

    // Session identification
    private String sessionId;
    private Long clientId;
    private String sessionToken;

    // Original Twilio webhook data
    private String callSid;
    private String messageSid;
    private String from;
    private String to;
    private String body; // For messages
    private String direction;

    // Decrypted client credentials for TEIA Core to use
    private String twilioAccountSid;
    private String twilioAuthToken;
    private String twilioCallNumber;
    private String twilioWhatsappNumber;

    private String elevenLabsApiKey;
    private String elevenLabsAgentId;

    private String openAiApiKey;
    private String openAiAssistantId;

    private String calApiKey;
    private String calUserId;
    private String calEventId;

    // Optional custom prompt
    private String customPrompt;
}
