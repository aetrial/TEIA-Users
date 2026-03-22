package com.teia.users.controllers;

import com.teia.users.services.BillingService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling Stripe webhook events.
 * This endpoint is unauthenticated but uses Stripe signature verification.
 */
@RestController
@RequestMapping("/billing/webhook")
@Tag(name = "Stripe Webhooks", description = "Stripe payment webhook handler")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final BillingService billingService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(BillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * Handle Stripe webhook events.
     * Verifies the webhook signature and processes checkout.session.completed events.
     */
    @PostMapping
    @Operation(summary = "Stripe webhook endpoint", description = "Handles Stripe payment events (no auth required, uses Stripe signature)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event received and processed"),
        @ApiResponse(responseCode = "400", description = "Invalid Stripe signature"),
        @ApiResponse(responseCode = "500", description = "Processing error")
    })
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @Parameter(
                name = "Stripe-Signature",
                description = "Stripe webhook signature for request verification",
                in = ParameterIn.HEADER,
                required = true
            )
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        // Verify the webhook signature
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        log.info("Received Stripe webhook event: {} ({})", event.getType(), event.getId());

        // Handle checkout.session.completed event
        if ("checkout.session.completed".equals(event.getType())) {
            try {
                // Extract session from the event
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElse(null);

                if (session != null) {
                    billingService.fulfillPurchase(session.getId());
                    log.info("Successfully processed checkout.session.completed for session {}",
                            session.getId());
                } else {
                    log.error("Could not deserialize session from event {}", event.getId());
                    return ResponseEntity.status(500).body("Failed to process event");
                }
            } catch (StripeException e) {
                log.error("Stripe error processing webhook: {}", e.getMessage(), e);
                return ResponseEntity.status(500).body("Stripe error: " + e.getMessage());
            } catch (Exception e) {
                log.error("Error processing webhook: {}", e.getMessage(), e);
                return ResponseEntity.status(500).body("Processing error: " + e.getMessage());
            }
        }

        // Return 200 for all events (even unhandled ones) to acknowledge receipt
        return ResponseEntity.ok("Received");
    }
}
