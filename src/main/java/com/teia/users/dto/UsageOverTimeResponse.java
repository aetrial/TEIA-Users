package com.teia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for GET /metrics/usage-over-time endpoint.
 * Contains credits consumed grouped by time period.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Usage data over time, grouped by specified period")
public class UsageOverTimeResponse {

    @Schema(description = "The period granularity (day, week, or month)", example = "day")
    private String period;

    @Schema(description = "List of usage entries per period")
    private List<UsagePeriodEntry> data;
}
