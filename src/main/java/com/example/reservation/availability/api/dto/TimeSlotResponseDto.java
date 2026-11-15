package com.example.reservation.availability.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Computed bookable time slot")
public record TimeSlotResponseDto(
        @Schema(description = "Assigned staff member ID")
        UUID staffId,

        @Schema(description = "Staff full name", example = "Marcus Vance")
        String staffName,

        @Schema(description = "Slot start time (UTC)")
        Instant startTime,

        @Schema(description = "Slot end time (UTC)")
        Instant endTime,

        @Schema(description = "Slot availability status")
        boolean available
) {}
