package com.example.reservation.payment.service;

import java.math.BigDecimal;

public interface StripeRefundService {

    /**
     * Issues a full or partial refund for a processed Stripe PaymentIntent.
     *
     * @param paymentIntentId The Stripe PaymentIntent identifier (pi_...)
     * @param amount          The amount to refund in standard currency units (e.g., 50.00 USD)
     * @param currency        The 3-letter ISO currency code
     * @param reason          Reason for the refund (e.g., customer cancellation)
     * @return StripeRefundResult containing refund ID and execution status
     */
    StripeRefundResult processRefund(String paymentIntentId, BigDecimal amount, String currency, String reason);
}
