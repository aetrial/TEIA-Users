package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for a full AgentPrompt.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Full agent prompt response")
public class AgentPromptResponse {

    @Schema(description = "Prompt ID", example = "1")
    private Long id;

    @Schema(description = "Version number", example = "3")
    private Integer version;

    @Schema(description = "The full system prompt content")
    private String content;

    @Schema(description = "Whether this is the active prompt", example = "true")
    private Boolean isActive;

    @Schema(description = "When the prompt was created")
    private LocalDateTime createdAt;
}
