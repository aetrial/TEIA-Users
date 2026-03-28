package com.teia.users.services;

import com.teia.users.dto.AgentPromptRequest;
import com.teia.users.dto.AgentPromptResponse;
import com.teia.users.dto.AgentPromptSummaryResponse;
import com.teia.users.models.AgentPrompt;
import com.teia.users.repositories.AgentPromptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing AgentPrompt entities.
 */
@Service
public class AgentPromptService {

    private static final Logger log = LoggerFactory.getLogger(AgentPromptService.class);

    private final AgentPromptRepository agentPromptRepository;

    public AgentPromptService(AgentPromptRepository agentPromptRepository) {
        this.agentPromptRepository = agentPromptRepository;
    }

    /**
     * Get the active prompt for a client.
     *
     * @param clientId The client ID
     * @return The active prompt, or empty if none exists
     */
    public Optional<AgentPromptResponse> getActivePrompt(Long clientId) {
        return agentPromptRepository.findActiveByClientId(clientId)
                .map(this::toResponse);
    }

    /**
     * Get the active prompt content for a client (for internal API).
     *
     * @param clientId The client ID
     * @return The active prompt content, or empty if none exists
     */
    public Optional<String> getActivePromptContent(Long clientId) {
        return agentPromptRepository.findActiveByClientId(clientId)
                .map(AgentPrompt::getContent);
    }

    /**
     * List all prompt versions for a client.
     *
     * @param clientId The client ID
     * @return List of prompt summaries with truncated content
     */
    public List<AgentPromptSummaryResponse> listPrompts(Long clientId) {
        return agentPromptRepository.findAllByClientIdOrderByVersionDesc(clientId)
                .stream()
                .map(ap -> AgentPromptSummaryResponse.fromContent(
                        ap.getId(),
                        ap.getVersion(),
                        ap.getContent(),
                        ap.getIsActive(),
                        ap.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Create a new prompt version for a client.
     * Auto-increments version number and sets as active.
     *
     * @param clientId The client ID
     * @param request  The prompt content
     * @return The created prompt
     */
    @Transactional
    public AgentPromptResponse createPrompt(Long clientId, AgentPromptRequest request) {
        log.info("[AgentPromptService] Creating new prompt for client: {}", clientId);

        // Deactivate all existing prompts for this client
        agentPromptRepository.deactivateAllByClientId(clientId);

        // Get next version number
        Integer maxVersion = agentPromptRepository.findMaxVersionByClientId(clientId);
        Integer nextVersion = maxVersion + 1;

        // Create new prompt
        AgentPrompt prompt = AgentPrompt.builder()
                .clientId(clientId)
                .content(request.getContent())
                .version(nextVersion)
                .isActive(true)
                .build();

        AgentPrompt saved = agentPromptRepository.save(prompt);
        log.info("[AgentPromptService] Created prompt id={}, version={} for client: {}",
                saved.getId(), saved.getVersion(), clientId);

        return toResponse(saved);
    }

    /**
     * Activate a specific prompt version for a client.
     *
     * @param clientId The client ID
     * @param promptId The prompt ID to activate
     * @return The activated prompt, or empty if not found/not owned
     */
    @Transactional
    public Optional<AgentPromptResponse> activatePrompt(Long clientId, Long promptId) {
        log.info("[AgentPromptService] Activating prompt id={} for client: {}", promptId, clientId);

        // Find the prompt and verify ownership
        Optional<AgentPrompt> promptOpt = agentPromptRepository.findByIdAndClientId(promptId, clientId);

        if (promptOpt.isEmpty()) {
            log.warn("[AgentPromptService] Prompt not found or not owned: id={}, clientId={}", promptId, clientId);
            return Optional.empty();
        }

        // Deactivate all prompts for this client
        agentPromptRepository.deactivateAllByClientId(clientId);

        // Activate the requested prompt
        AgentPrompt prompt = promptOpt.get();
        prompt.setIsActive(true);
        AgentPrompt saved = agentPromptRepository.save(prompt);

        log.info("[AgentPromptService] Activated prompt id={}, version={} for client: {}",
                saved.getId(), saved.getVersion(), clientId);

        return Optional.of(toResponse(saved));
    }

    /**
     * Convert entity to response DTO.
     */
    private AgentPromptResponse toResponse(AgentPrompt prompt) {
        return AgentPromptResponse.builder()
                .id(prompt.getId())
                .version(prompt.getVersion())
                .content(prompt.getContent())
                .isActive(prompt.getIsActive())
                .createdAt(prompt.getCreatedAt())
                .build();
    }
}
