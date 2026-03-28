package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Aggregated provider usage summary for a client across all sessions")
public class ProviderSummaryResponse {

    @Schema(description = "Twilio usage summary")
    private TwilioSummary twilio;

    @Schema(description = "OpenAI usage summary")
    private OpenAiSummary openAi;

    @Schema(description = "ElevenLabs usage summary")
    private ElevenLabsSummary elevenLabs;

    @Schema(description = "Stripe billing summary")
    private StripeSummary stripe;

    @Data
    @Builder
    @Schema(description = "Aggregated Twilio usage metrics")
    public static class TwilioSummary {

        @Schema(description = "Total call minutes across all sessions", example = "1250")
        private long totalCallMinutes;

        @Schema(description = "Total SMS messages sent across all sessions", example = "42")
        private long totalSmsSent;
    }

    @Data
    @Builder
    @Schema(description = "Aggregated OpenAI token usage")
    public static class OpenAiSummary {

        @Schema(description = "Total input tokens consumed across all sessions", example = "150000")
        private long totalInputTokens;

        @Schema(description = "Total output tokens generated across all sessions", example = "75000")
        private long totalOutputTokens;
    }

    @Data
    @Builder
    @Schema(description = "Aggregated ElevenLabs character usage")
    public static class ElevenLabsSummary {

        @Schema(description = "Total characters synthesized across all sessions", example = "500000")
        private long totalCharacters;
    }

    @Data
    @Builder
    @Schema(description = "Aggregated Stripe billing totals")
    public static class StripeSummary {

        @Schema(description = "Total amount paid via Stripe", example = "250.00")
        private BigDecimal totalPaid;

        @Schema(description = "Currency code for all payments", example = "USD")
        private String currency;

        @Schema(description = "Timestamp of the most recent payment", example = "2026-03-27T14:30:00")
        private LocalDateTime lastPaymentAt;
    }
}
