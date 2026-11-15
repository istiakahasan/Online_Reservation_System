package com.example.reservation.reservation.service;

import java.math.BigDecimal;
import java.util.UUID;

public record CancelReservationResult(
        UUID reservationId,
        String status,
        boolean isCancelled,
        boolean wasPaid,
        boolean refundTriggered,
        BigDecimal refundAmount,
        String refundId,
        String refundStatus,
        String cancellationReason
) {}

