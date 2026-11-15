package com.example.reservation.availability.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Criteria for querying staff availability")
public record AvailabilityQueryDto(
        @Schema(description = "Business tenant ID")
        @NotNull(message = "businessId is required")
        UUID businessId,

        @Schema(description = "Service offering ID")
        @NotNull(message = "serviceId is required")
        UUID serviceId,

        @Schema(description = "Optional specific staff member ID")
        UUID staffId,

        @Schema(description = "Start search window timestamp (UTC)")
        @NotNull(message = "fromTime is required")
        Instant fromTime,

        @Schema(description = "End search window timestamp (UTC)")
        @NotNull(message = "toTime is required")
        Instant toTime
) {}

