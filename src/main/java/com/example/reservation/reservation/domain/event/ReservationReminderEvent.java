package com.example.reservation.reservation.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationReminderEvent(
        UUID reservationId,
        UUID businessId,
        UUID customerId,
        UUID staffId,
        Instant startTime,
        Instant occurredAt
) {}
