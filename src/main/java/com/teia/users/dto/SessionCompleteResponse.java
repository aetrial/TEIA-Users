package com.teia.users.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO for session completion response.
 */
@Data
@Builder
public class SessionCompleteResponse {

    private String sessionId;
    private String status;
    private BigDecimal creditsReserved;
    private BigDecimal creditsUsed;
    private BigDecimal creditsDifference; // positive = refunded, negative = additional charge
    private BigDecimal newBalance;
}
