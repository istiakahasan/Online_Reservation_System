package com.example.reservation.service.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Bookable service offering response")
public record ServiceResponseDto(
        @Schema(description = "Service unique ID")
        UUID id,

        @Schema(description = "Tenant business ID")
        UUID businessId,

        @Schema(description = "Service name", example = "Deep Tissue Massage 60m")
        String name,

        @Schema(description = "Service description")
        String description,

        @Schema(description = "Duration in minutes", example = "60")
        Integer durationMinutes,

        @Schema(description = "Price amount", example = "85.00")
        BigDecimal priceAmount,

        @Schema(description = "Currency", example = "USD")
        String currency,

        @Schema(description = "Active status flag")
        Boolean isActive
) {}
