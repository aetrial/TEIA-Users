package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new AgentPrompt.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a new agent prompt version")
public class AgentPromptRequest {

    @NotBlank(message = "Content cannot be empty")
    @Size(max = 4000, message = "Content cannot exceed 4000 characters")
    @Schema(description = "The system prompt content", example = "You are a helpful assistant...", maxLength = 4000)
    private String content;
}
