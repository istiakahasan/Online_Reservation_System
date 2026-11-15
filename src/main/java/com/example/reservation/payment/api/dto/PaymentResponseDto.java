package com.example.reservation.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Payment transaction response")
public record PaymentResponseDto(
        UUID id,
        UUID businessId,
        UUID reservationId,
        String stripePaymentIntentId,
        String clientSecret,
        BigDecimal amount,
        String currency,
        String status
) {}
