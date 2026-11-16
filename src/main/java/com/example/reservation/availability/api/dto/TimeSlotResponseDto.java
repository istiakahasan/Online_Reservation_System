package com.example.reservation.availability.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

@Schema(description = "Computed bookable appointment slot")
public record TimeSlotResponseDto(
        @Schema(description = "Assigned staff member ID")
        UUID staffId,

        @Schema(description = "Start timestamp in UTC")
        Instant startInstant,

        @Schema(description = "End timestamp in UTC")
        Instant endInstant,

        @Schema(description = "Local start time in business timezone")
        ZonedDateTime localStartTime,

        @Schema(description = "Local end time in business timezone")
        ZonedDateTime localEndTime,

        @Schema(description = "Duration in minutes")
        long durationMinutes,

        @Schema(description = "Whether the slot is currently open for booking")
        boolean available
) {}
