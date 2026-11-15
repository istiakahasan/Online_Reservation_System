package com.example.reservation.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Reservation booking details")
public record ReservationResponseDto(
        @Schema(description = "Reservation ID")
        UUID id,

        @Schema(description = "Business tenant ID")
        UUID businessId,

        @Schema(description = "Customer ID")
        UUID customerId,

        @Schema(description = "Service ID")
        UUID serviceId,

        @Schema(description = "Staff member ID")
        UUID staffId,

        @Schema(description = "Reservation start time (UTC)")
        Instant startTime,

        @Schema(description = "Reservation end time (UTC)")
        Instant endTime,

        @Schema(description = "Current lifecycle status", example = "PENDING")
        String status,

        @Schema(description = "Total booking price")
        BigDecimal totalPrice,

        @Schema(description = "Currency", example = "USD")
        String currency,

        @Schema(description = "Hold expiration timestamp, if pending")
        Instant holdExpiresAt,

        @Schema(description = "Booking notes")
        String notes
) {}
