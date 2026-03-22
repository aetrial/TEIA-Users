package com.teia.users.controllers;

import com.teia.users.dto.CheckoutRequest;
import com.teia.users.dto.CheckoutResponse;
import com.teia.users.dto.CreditTransactionResponse;
import com.teia.users.dto.ErrorResponse;
import com.teia.users.models.enums.CreditBundle;
import com.teia.users.security.JwtUserPrincipal;
import com.teia.users.services.BillingService;
import com.stripe.exception.StripeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for credit purchasing and billing history.
 */
@RestController
@RequestMapping("/billing")
@Tag(name = "Billing", description = "Credit purchases and transaction history")
@SecurityRequirement(name = "bearerAuth")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * Create a Stripe checkout session for purchasing credits.
     */
    @PostMapping("/checkout")
    @Operation(summary = "Create checkout session", description = "Creates a Stripe checkout session for the selected credit bundle")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Checkout session created successfully",
            content = @Content(schema = @Schema(implementation = CheckoutResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid bundle ID"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not authorized (requires CLIENT role)"),
        @ApiResponse(responseCode = "404", description = "Client not found"),
        @ApiResponse(responseCode = "500", description = "Stripe API error")
    })
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> createCheckout(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Credit bundle selection",
                required = true,
                content = @Content(schema = @Schema(implementation = CheckoutRequest.class))
            )
            @Valid @RequestBody CheckoutRequest request,
                                            HttpServletRequest httpRequest) {
        Long clientId = getAuthenticatedClientId();

        // Validate bundle ID
        CreditBundle bundle;
        try {
            bundle = CreditBundle.fromId(request.getBundleId());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid bundle ID requested: {}", request.getBundleId());
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    400,
                    "Bad Request",
                    "Invalid bundle ID. Valid values: BUNDLE_100, BUNDLE_500, BUNDLE_1000",
                    httpRequest.getRequestURI()
            ));
        }

        try {
            String checkoutUrl = billingService.createCheckoutSession(clientId, bundle);
            return ResponseEntity.ok(new CheckoutResponse(checkoutUrl));
        } catch (IllegalArgumentException e) {
            log.error("Client not found: {}", clientId);
            return ResponseEntity.status(404).body(new ErrorResponse(
                    404,
                    "Not Found",
                    e.getMessage(),
                    httpRequest.getRequestURI()
            ));
        } catch (StripeException e) {
            log.error("Stripe error creating checkout session: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new ErrorResponse(
                    500,
                    "Internal Server Error",
                    "Failed to create checkout session",
                    httpRequest.getRequestURI()
            ));
        }
    }

    /**
     * Get transaction history for the authenticated client.
     */
    @GetMapping("/history")
    @Operation(summary = "Get transaction history", description = "Returns the credit transaction history for the authenticated client")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction history retrieved successfully",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreditTransactionResponse.class)))),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not authorized (requires CLIENT role)")
    })
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<CreditTransactionResponse>> getHistory() {
        Long clientId = getAuthenticatedClientId();

        List<CreditTransactionResponse> history = billingService.getTransactionHistory(clientId)
                .stream()
                .map(CreditTransactionResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(history);
    }

    /**
     * Extract the client ID from the security context.
     */
    private Long getAuthenticatedClientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtUserPrincipal principal = (JwtUserPrincipal) authentication.getPrincipal();
        return principal.getUserId();
    }
}
