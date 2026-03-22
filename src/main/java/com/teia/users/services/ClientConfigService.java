package com.teia.users.services;

import com.teia.users.dto.ClientConfigRequest;
import com.teia.users.dto.ClientConfigResponse;
import com.teia.users.dto.ClientSummaryResponse;
import com.teia.users.models.ClientConfig;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.enums.Role;
import com.teia.users.repositories.ClientConfigRepository;
import com.teia.users.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing client configurations.
 * Handles CRUD operations, field masking for sensitive data, and partial updates.
 */
@Service
public class ClientConfigService {

    private final ClientConfigRepository clientConfigRepository;
    private final UserRepository userRepository;

    public ClientConfigService(ClientConfigRepository clientConfigRepository, UserRepository userRepository) {
        this.clientConfigRepository = clientConfigRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates or overwrites the full configuration for a client.
     */
    @Transactional
    public ClientConfigResponse createOrUpdateConfig(Long clientId, ClientConfigRequest request) {
        TeiaUser client = userRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException("Client not found with id: " + clientId));

        ClientConfig config = clientConfigRepository.findByClientId(clientId)
                .orElse(new ClientConfig());

        config.setClient(client);
        applyAllFields(config, request);

        ClientConfig saved = clientConfigRepository.save(config);
        return toMaskedResponse(saved);
    }

    /**
     * Retrieves the configuration for a client with sensitive fields masked.
     */
    @Transactional(readOnly = true)
    public ClientConfigResponse getConfig(Long clientId) {
        ClientConfig config = clientConfigRepository.findByClientId(clientId)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for client: " + clientId));

        return toMaskedResponse(config);
    }

    /**
     * Partially updates the configuration (only provided fields are updated).
     */
    @Transactional
    public ClientConfigResponse patchConfig(Long clientId, ClientConfigRequest request) {
        ClientConfig config = clientConfigRepository.findByClientId(clientId)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found for client: " + clientId));

        applyNonNullFields(config, request);

        ClientConfig saved = clientConfigRepository.save(config);
        return toMaskedResponse(saved);
    }

    /**
     * Lists all clients with their config status and credit balance.
     * Only includes users with CLIENT role.
     */
    @Transactional(readOnly = true)
    public List<ClientSummaryResponse> listClientsWithConfigStatus() {
        List<TeiaUser> clients = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.CLIENT)
                .collect(Collectors.toList());

        return clients.stream()
                .map(client -> ClientSummaryResponse.builder()
                        .clientId(client.getId())
                        .name(client.getName())
                        .email(client.getEmail())
                        .hasConfig(clientConfigRepository.existsByClientId(client.getId()))
                        .creditBalance(client.getCreditBalance())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Masks a sensitive value by showing "****" + last 4 characters.
     * If the value is null or shorter than 4 characters, returns "****".
     */
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    /**
     * Converts entity to response DTO with masked sensitive fields.
     */
    private ClientConfigResponse toMaskedResponse(ClientConfig config) {
        return ClientConfigResponse.builder()
                .id(config.getId())
                .clientId(config.getClient().getId())
                .twilioAccountSid(config.getTwilioAccountSid())
                .twilioAuthToken(maskSensitiveValue(config.getTwilioAuthToken()))
                .twilioCallNumber(config.getTwilioCallNumber())
                .twilioWhatsappNumber(config.getTwilioWhatsappNumber())
                .elevenLabsApiKey(maskSensitiveValue(config.getElevenLabsApiKey()))
                .elevenLabsAgentId(config.getElevenLabsAgentId())
                .openAiApiKey(maskSensitiveValue(config.getOpenAiApiKey()))
                .openAiAssistantId(config.getOpenAiAssistantId())
                .calApiKey(maskSensitiveValue(config.getCalApiKey()))
                .calUserId(config.getCalUserId())
                .calEventId(config.getCalEventId())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    /**
     * Applies all fields from request to entity (for full create/update).
     */
    private void applyAllFields(ClientConfig config, ClientConfigRequest request) {
        config.setTwilioAccountSid(request.getTwilioAccountSid());
        config.setTwilioAuthToken(request.getTwilioAuthToken());
        config.setTwilioCallNumber(request.getTwilioCallNumber());
        config.setTwilioWhatsappNumber(request.getTwilioWhatsappNumber());
        config.setElevenLabsApiKey(request.getElevenLabsApiKey());
        config.setElevenLabsAgentId(request.getElevenLabsAgentId());
        config.setOpenAiApiKey(request.getOpenAiApiKey());
        config.setOpenAiAssistantId(request.getOpenAiAssistantId());
        config.setCalApiKey(request.getCalApiKey());
        config.setCalUserId(request.getCalUserId());
        config.setCalEventId(request.getCalEventId());
    }

    /**
     * Applies only non-null fields from request to entity (for PATCH).
     */
    private void applyNonNullFields(ClientConfig config, ClientConfigRequest request) {
        if (request.getTwilioAccountSid() != null) {
            config.setTwilioAccountSid(request.getTwilioAccountSid());
        }
        if (request.getTwilioAuthToken() != null) {
            config.setTwilioAuthToken(request.getTwilioAuthToken());
        }
        if (request.getTwilioCallNumber() != null) {
            config.setTwilioCallNumber(request.getTwilioCallNumber());
        }
        if (request.getTwilioWhatsappNumber() != null) {
            config.setTwilioWhatsappNumber(request.getTwilioWhatsappNumber());
        }
        if (request.getElevenLabsApiKey() != null) {
            config.setElevenLabsApiKey(request.getElevenLabsApiKey());
        }
        if (request.getElevenLabsAgentId() != null) {
            config.setElevenLabsAgentId(request.getElevenLabsAgentId());
        }
        if (request.getOpenAiApiKey() != null) {
            config.setOpenAiApiKey(request.getOpenAiApiKey());
        }
        if (request.getOpenAiAssistantId() != null) {
            config.setOpenAiAssistantId(request.getOpenAiAssistantId());
        }
        if (request.getCalApiKey() != null) {
            config.setCalApiKey(request.getCalApiKey());
        }
        if (request.getCalUserId() != null) {
            config.setCalUserId(request.getCalUserId());
        }
        if (request.getCalEventId() != null) {
            config.setCalEventId(request.getCalEventId());
        }
    }

    // Custom exceptions
    public static class ClientNotFoundException extends RuntimeException {
        public ClientNotFoundException(String message) {
            super(message);
        }
    }

    public static class ConfigNotFoundException extends RuntimeException {
        public ConfigNotFoundException(String message) {
            super(message);
        }
    }
}
