package com.teia.users.models;

import com.teia.users.models.enums.CreditTransactionType;
import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records all credit transactions for audit and billing purposes.
 * Positive amounts represent credits added (purchase, refund).
 * Negative amounts represent credits consumed (reservation, deduction).
 */
@Entity
@Table(name = "credit_transaction", indexes = {
    @Index(name = "idx_credit_transaction_client_id", columnList = "client_id"),
    @Index(name = "idx_credit_transaction_type", columnList = "type"),
    @Index(name = "idx_credit_transaction_created_at", columnList = "created_at"),
    @Index(name = "idx_credit_transaction_reference_id", columnList = "reference_id")
})
@Data
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private CreditTransactionType type;

    @Column(name = "reference_id", length = 255)
    private String referenceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CreditTransaction() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Creates a new credit transaction.
     *
     * @param clientId    The client this transaction belongs to
     * @param amount      The amount (positive for credits added, negative for credits consumed)
     * @param type        The type of transaction
     * @param referenceId Optional reference (session ID, Stripe payment intent, etc.)
     */
    public CreditTransaction(Long clientId, BigDecimal amount, CreditTransactionType type, String referenceId) {
        this.clientId = clientId;
        this.amount = amount;
        this.type = type;
        this.referenceId = referenceId;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Factory method for creating a reservation transaction.
     */
    public static CreditTransaction reservation(Long clientId, BigDecimal amount, String sessionId) {
        return new CreditTransaction(clientId, amount.negate(), CreditTransactionType.RESERVATION, sessionId);
    }

    /**
     * Factory method for creating a refund transaction.
     */
    public static CreditTransaction refund(Long clientId, BigDecimal amount, String sessionId) {
        return new CreditTransaction(clientId, amount, CreditTransactionType.REFUND, sessionId);
    }

    /**
     * Factory method for creating a deduction transaction (additional charge beyond reservation).
     */
    public static CreditTransaction deduction(Long clientId, BigDecimal amount, String sessionId) {
        return new CreditTransaction(clientId, amount.negate(), CreditTransactionType.DEDUCTION, sessionId);
    }
}
