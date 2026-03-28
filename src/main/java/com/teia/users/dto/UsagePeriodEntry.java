package com.teia.users.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single entry in the usage-over-time response representing credits consumed in a period.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Credits consumed in a single time period")
public class UsagePeriodEntry {

    @Schema(description = "Start date of the period", example = "2026-03-01")
    private LocalDate periodStart;

    @Schema(description = "Total credits consumed in this period", example = "25.75")
    private BigDecimal creditsConsumed;
}
