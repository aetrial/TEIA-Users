package com.teia.users.services;

import com.teia.users.models.CreditTransaction;
import com.teia.users.models.TeiaUser;
import com.teia.users.models.enums.CreditBundle;
import com.teia.users.models.enums.CreditTransactionType;
import com.teia.users.repositories.CreditTransactionRepository;
import com.teia.users.repositories.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for handling credit purchases via Stripe.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final UserRepository userRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    public BillingService(UserRepository userRepository,
                          CreditTransactionRepository creditTransactionRepository) {
        this.userRepository = userRepository;
        this.creditTransactionRepository = creditTransactionRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    /**
     * Creates a Stripe Checkout session for the given bundle.
     *
     * @param clientId The client purchasing credits
     * @param bundle   The credit bundle to purchase
     * @return The Stripe checkout URL
     * @throws StripeException if Stripe API call fails
     */
    public String createCheckoutSession(Long clientId, CreditBundle bundle) throws StripeException {
        // Verify client exists
        if (!userRepository.existsById(clientId)) {
            throw new IllegalArgumentException("Client not found: " + clientId);
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(bundle.getPriceInCents())
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(bundle.getDescription())
                                                                .setDescription("TEIA Platform Credits")
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .putMetadata("client_id", clientId.toString())
                .putMetadata("bundle_id", bundle.name())
                .putMetadata("credits", String.valueOf(bundle.getCredits()))
                .build();

        Session session = Session.create(params);
        log.info("Created Stripe checkout session {} for client {} bundle {}",
                session.getId(), clientId, bundle.name());

        return session.getUrl();
    }

    /**
     * Fulfills a successful credit purchase.
     * This method is idempotent - if the purchase was already fulfilled, it does nothing.
     *
     * @param stripeSessionId The Stripe checkout session ID
     * @throws StripeException if Stripe API call fails
     */
    @Transactional
    public void fulfillPurchase(String stripeSessionId) throws StripeException {
        // Check if already fulfilled (idempotency)
        List<CreditTransaction> existing = creditTransactionRepository.findByReferenceId(stripeSessionId);
        if (!existing.isEmpty()) {
            log.info("Purchase already fulfilled for session {}", stripeSessionId);
            return;
        }

        // Retrieve session from Stripe to get metadata
        Session session = Session.retrieve(stripeSessionId);

        String clientIdStr = session.getMetadata().get("client_id");
        String bundleIdStr = session.getMetadata().get("bundle_id");
        String creditsStr = session.getMetadata().get("credits");

        if (clientIdStr == null || creditsStr == null) {
            log.error("Missing metadata in Stripe session {}", stripeSessionId);
            throw new IllegalStateException("Missing required metadata in Stripe session");
        }

        Long clientId = Long.parseLong(clientIdStr);
        BigDecimal credits = new BigDecimal(creditsStr);

        // Find and update user
        TeiaUser user = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("Client not found: " + clientId));

        // Add credits to balance
        BigDecimal currentBalance = user.getCreditBalance() != null ? user.getCreditBalance() : BigDecimal.ZERO;
        user.setCreditBalance(currentBalance.add(credits));
        userRepository.save(user);

        // Record transaction
        CreditTransaction transaction = new CreditTransaction(
                clientId,
                credits,
                CreditTransactionType.PURCHASE,
                stripeSessionId
        );
        creditTransactionRepository.save(transaction);

        log.info("Fulfilled purchase for client {}: {} credits added (session {})",
                clientId, credits, stripeSessionId);
    }

    /**
     * Gets the transaction history for a client.
     *
     * @param clientId The client ID
     * @return List of transactions sorted by date descending
     */
    public List<CreditTransaction> getTransactionHistory(Long clientId) {
        return creditTransactionRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }
}
