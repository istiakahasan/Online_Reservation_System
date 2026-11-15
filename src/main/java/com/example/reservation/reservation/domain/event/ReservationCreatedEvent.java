package com.example.reservation.reservation.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedEvent(
        UUID reservationId,
        UUID businessId,
        UUID customerId,
        UUID serviceId,
        UUID staffId,
        Instant startTime,
        Instant endTime,
        BigDecimal totalPrice,
        String currency,
        Instant holdExpiresAt,
        Instant occurredAt
) {}

