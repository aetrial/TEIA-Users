package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for internal prompt content endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Agent prompt content for internal API")
public class AgentPromptContentResponse {

    @Schema(description = "The system prompt content", example = "You are a helpful assistant...")
    private String content;
}
