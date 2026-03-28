package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "ElevenLabs character usage metrics broken down by session with aggregate totals")
public class ElevenLabsMetricsResponse {

    @Schema(description = "Per-session ElevenLabs character usage records")
    private List<ElevenLabsSessionMetric> sessions;

    @Schema(description = "Aggregate ElevenLabs character totals across all sessions")
    private ElevenLabsAggregate aggregate;

    @Data
    @Builder
    @Schema(description = "ElevenLabs character usage for a single session")
    public static class ElevenLabsSessionMetric {

        @Schema(description = "Unique session identifier", example = "sess_abc123")
        private String sessionId;

        @Schema(description = "Characters synthesized in this session", example = "5000")
        private Integer characters;

        @Schema(description = "Timestamp when the session occurred", example = "2026-03-27T14:30:00")
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @Schema(description = "Aggregate ElevenLabs character usage across all sessions")
    public static class ElevenLabsAggregate {

        @Schema(description = "Total characters synthesized across all sessions", example = "500000")
        private long totalCharacters;
    }
}
