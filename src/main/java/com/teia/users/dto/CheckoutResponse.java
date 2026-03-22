package com.teia.users.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing the Stripe checkout URL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {

    private String checkoutUrl;
}
