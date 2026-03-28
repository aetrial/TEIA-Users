package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for AgentPrompt list items with truncated content.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Agent prompt summary with truncated content preview")
public class AgentPromptSummaryResponse {

    @Schema(description = "Prompt ID", example = "1")
    private Long id;

    @Schema(description = "Version number", example = "3")
    private Integer version;

    @Schema(description = "First 100 characters of the prompt content", example = "You are a helpful assistant that...")
    private String contentPreview;

    @Schema(description = "Whether this is the active prompt", example = "true")
    private Boolean isActive;

    @Schema(description = "When the prompt was created")
    private LocalDateTime createdAt;

    /**
     * Creates a summary response from full content, truncating to 100 chars.
     */
    public static AgentPromptSummaryResponse fromContent(Long id, Integer version, String content,
                                                          Boolean isActive, LocalDateTime createdAt) {
        String preview = content != null && content.length() > 100
            ? content.substring(0, 100) + "..."
            : content;
        return AgentPromptSummaryResponse.builder()
                .id(id)
                .version(version)
                .contentPreview(preview)
                .isActive(isActive)
                .createdAt(createdAt)
                .build();
    }
}
