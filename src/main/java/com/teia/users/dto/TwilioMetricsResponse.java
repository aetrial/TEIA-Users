package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "Twilio usage metrics broken down by session with aggregate totals")
public class TwilioMetricsResponse {

    @Schema(description = "Per-session Twilio usage records")
    private List<TwilioSessionMetric> sessions;

    @Schema(description = "Aggregate Twilio totals across all sessions")
    private TwilioAggregate aggregate;

    @Data
    @Builder
    @Schema(description = "Twilio usage metrics for a single session")
    public static class TwilioSessionMetric {

        @Schema(description = "Unique session identifier", example = "sess_abc123")
        private String sessionId;

        @Schema(description = "Total call duration for this session in seconds", example = "180")
        private Integer callDurationSeconds;

        @Schema(description = "Number of SMS messages sent in this session", example = "3")
        private Integer smsCount;

        @Schema(description = "Timestamp when the session occurred", example = "2026-03-27T14:30:00")
        private LocalDateTime timestamp;
    }

    @Data
    @Builder
    @Schema(description = "Aggregate Twilio usage across all sessions")
    public static class TwilioAggregate {

        @Schema(description = "Total call minutes across all sessions", example = "1250")
        private long totalCallMinutes;

        @Schema(description = "Total SMS messages sent across all sessions", example = "42")
        private long totalSmsSent;
    }
}
