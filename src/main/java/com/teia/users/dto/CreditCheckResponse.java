package com.teia.users.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO for credit check response.
 */
@Data
@Builder
public class CreditCheckResponse {

    private Long clientId;
    private BigDecimal creditBalance;
    private boolean hasCredits;
}
