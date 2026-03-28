package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "OpenAI token usage metrics broken down by session with aggregate totals")
public class OpenAiMetricsResponse {

    @Schema(description = "Per-session OpenAI token usage records")
    private List<OpenAiSessionMetric> sessions;

    @Schema(description = "Aggregate OpenAI token totals across all sessions")
    private OpenAiAggregate aggregate;

    @Data
    @Builder
    @Schema(description = "OpenAI token usage for a single session")
    public static class OpenAiSessionMetric {

        @Schema(description = "Unique session identifier", example = "sess_abc123")
        private String sessionId;

        @Schema(description = "Input tokens consumed in this session", example = "1500")
        private Integer inputTokens;

        @Schema(description = "Output tokens generated in this session", example = "800")
        private Integer outputTokens;

        @Schema(description = "Timestamp when the session occurred", example = "2026-03-27T14:30:00")
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @Schema(description = "Aggregate OpenAI token usage across all sessions")
    public static class OpenAiAggregate {

        @Schema(description = "Total input tokens consumed across all sessions", example = "150000")
        private long totalInputTokens;

        @Schema(description = "Total output tokens generated across all sessions", example = "75000")
        private long totalOutputTokens;
    }
}
