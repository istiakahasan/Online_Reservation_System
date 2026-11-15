package com.example.reservation.reservation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Create new reservation request")
public record CreateReservationRequestDto(
        @Schema(description = "Business tenant ID")
        @NotNull(message = "businessId is required")
        UUID businessId,

        @Schema(description = "Customer user ID")
        @NotNull(message = "customerId is required")
        UUID customerId,

        @Schema(description = "Service offering ID")
        @NotNull(message = "serviceId is required")
        UUID serviceId,

        @Schema(description = "Assigned staff member ID")
        @NotNull(message = "staffId is required")
        UUID staffId,

        @Schema(description = "Reservation start time (UTC)")
        @NotNull(message = "startTime is required")
        Instant startTime,

        @Schema(description = "Optional booking notes / special requests")
        String notes
) {}
