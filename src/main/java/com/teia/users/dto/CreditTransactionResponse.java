package com.teia.users.dto;

import com.teia.users.models.CreditTransaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for credit transaction history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditTransactionResponse {

    private Long id;
    private BigDecimal amount;
    private String type;
    private String referenceId;
    private LocalDateTime createdAt;

    /**
     * Create a response from a CreditTransaction entity.
     */
    public static CreditTransactionResponse fromEntity(CreditTransaction transaction) {
        return new CreditTransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType().name(),
                transaction.getReferenceId(),
                transaction.getCreatedAt()
        );
    }
}
