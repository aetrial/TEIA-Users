package com.teia.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "Details of a single Stripe payment transaction")
public class StripePaymentResponse {

    @Schema(description = "Stripe PaymentIntent ID", example = "pi_3NkFh2CZ6qsJgndJ0xmF...")
    private String paymentIntentId;

    @Schema(description = "Payment amount charged", example = "25.00")
    private BigDecimal amount;

    @Schema(description = "ISO 4217 currency code", example = "usd")
    private String currency;

    @Schema(description = "Payment status from Stripe", example = "succeeded")
    private String status;

    /** Null when the entry comes from Stripe API only (no matching CreditTransaction). */
    @Schema(description = "Credits purchased with this payment; null if no matching credit transaction", example = "100.00")
    private BigDecimal creditsPurchased;

    @Schema(description = "Timestamp when the payment was created", example = "2026-03-27T10:15:30")
    private LocalDateTime createdAt;
}
