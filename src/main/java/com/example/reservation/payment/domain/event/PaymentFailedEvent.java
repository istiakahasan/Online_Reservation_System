package com.example.reservation.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID reservationId,
        UUID businessId,
        UUID customerId,
        String paymentIntentId,
        String failureMessage,
        Instant occurredAt
) {}
