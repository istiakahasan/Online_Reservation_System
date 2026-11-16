package com.example.reservation.payment.service;

import com.example.reservation.payment.api.dto.PaymentIntentResponseDto;
import java.util.UUID;

public interface StripeService {

    /**
     * Creates a Stripe PaymentIntent tied to the reservation and returns the clientSecret.
     *
     * @param reservationId UUID of the target reservation
     * @return PaymentIntentResponseDto containing the clientSecret
     */
    PaymentIntentResponseDto createPaymentIntent(UUID reservationId);

    /**
     * Verifies Stripe signature and processes webhook events idempotently.
     *
     * @param payload   Raw JSON string received from Stripe webhook
     * @param sigHeader Stripe-Signature header value
     */
    void processWebhookEvent(String payload, String sigHeader);
}
