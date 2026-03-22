package com.teia.users.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO for usage report response.
 */
@Data
@Builder
public class UsageReportResponse {

    private String sessionId;
    private BigDecimal creditsUsed;
    private BigDecimal newBalance;
}
