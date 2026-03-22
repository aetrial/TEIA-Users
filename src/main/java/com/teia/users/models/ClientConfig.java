package com.teia.users.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.teia.users.security.EncryptedStringConverter;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores third-party service credentials and configuration for a TEIA client.
 * Sensitive fields (API keys, auth tokens) are encrypted at rest using AES-256-GCM.
 */
@Entity
@Table(name = "client_config", indexes = {
    @Index(name = "idx_client_config_client_id", columnList = "client_id", unique = true)
})
@Data
public class ClientConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    @JsonBackReference
    private TeiaUser client;

    // Twilio configuration
    @Column(name = "twilio_account_sid", length = 255)
    private String twilioAccountSid;

    @Column(name = "twilio_auth_token", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String twilioAuthToken;

    @Column(name = "twilio_call_number", length = 50)
    private String twilioCallNumber;

    @Column(name = "twilio_whatsapp_number", length = 50)
    private String twilioWhatsappNumber;

    // ElevenLabs configuration
    @Column(name = "eleven_labs_api_key", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String elevenLabsApiKey;

    @Column(name = "eleven_labs_agent_id", length = 255)
    private String elevenLabsAgentId;

    // OpenAI configuration
    @Column(name = "open_ai_api_key", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String openAiApiKey;

    @Column(name = "open_ai_assistant_id", length = 255)
    private String openAiAssistantId;

    // Cal.com configuration
    @Column(name = "cal_api_key", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String calApiKey;

    @Column(name = "cal_user_id", length = 255)
    private String calUserId;

    @Column(name = "cal_event_id", length = 255)
    private String calEventId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ClientConfig() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
