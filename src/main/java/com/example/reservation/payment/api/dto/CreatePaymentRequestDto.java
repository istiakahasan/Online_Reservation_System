package com.example.reservation.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Create payment intent request")
public record CreatePaymentRequestDto(
        @Schema(description = "Reservation ID")
        @NotNull(message = "reservationId is required")
        UUID reservationId,

        @Schema(description = "Payment amount")
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be positive")
        BigDecimal amount,

        @Schema(description = "Currency", example = "USD")
        String currency
) {}
