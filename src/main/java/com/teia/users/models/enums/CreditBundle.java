package com.teia.users.models.enums;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Credit bundles available for purchase.
 * Prices are defined in cents for Stripe integration.
 */
public enum CreditBundle {
    BUNDLE_100(100, 1000L, "100 Credits"),
    BUNDLE_500(500, 4000L, "500 Credits"),
    BUNDLE_1000(1000, 7000L, "1000 Credits");

    private final int credits;
    private final long priceInCents;
    private final String description;

    CreditBundle(int credits, long priceInCents, String description) {
        this.credits = credits;
        this.priceInCents = priceInCents;
        this.description = description;
    }

    public int getCredits() {
        return credits;
    }

    public long getPriceInCents() {
        return priceInCents;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getCreditsAsBigDecimal() {
        return BigDecimal.valueOf(credits);
    }

    /**
     * Find a bundle by its name/ID.
     *
     * @param bundleId The bundle identifier (e.g., "BUNDLE_100")
     * @return The matching CreditBundle
     * @throws IllegalArgumentException if no bundle matches
     */
    public static CreditBundle fromId(String bundleId) {
        return Arrays.stream(values())
                .filter(b -> b.name().equals(bundleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid bundle ID: " + bundleId));
    }
}
