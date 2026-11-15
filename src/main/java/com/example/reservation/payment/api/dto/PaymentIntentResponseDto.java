package com.example.reservation.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Stripe PaymentIntent response payload for client-side Stripe Elements")
public record PaymentIntentResponseDto(
        @Schema(description = "Stripe client secret used to confirm payment on client")
        String clientSecret,

        @Schema(description = "Stripe PaymentIntent identifier (pi_...)")
        String paymentIntentId,

        @Schema(description = "Total payable booking amount")
        BigDecimal amount,

        @Schema(description = "Currency code (e.g. USD)")
        String currency,

        @Schema(description = "Associated reservation UUID")
        UUID reservationId
) {}
