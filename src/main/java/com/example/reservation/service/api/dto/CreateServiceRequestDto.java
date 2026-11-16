package com.example.reservation.service.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Create bookable service request")
public record CreateServiceRequestDto(
        @Schema(description = "Business tenant ID")
        @NotNull(message = "businessId is required")
        UUID businessId,

        @Schema(description = "Service offering name", example = "Deep Tissue Massage 60m")
        @NotBlank(message = "Service name is required")
        String name,

        @Schema(description = "Service detailed description")
        String description,

        @Schema(description = "Duration in minutes", example = "60")
        @NotNull(message = "durationMinutes is required")
        @Positive(message = "durationMinutes must be greater than zero")
        Integer durationMinutes,

        @Schema(description = "Price amount", example = "85.00")
        @NotNull(message = "priceAmount is required")
        @Positive(message = "priceAmount must be positive")
        BigDecimal priceAmount,

        @Schema(description = "ISO-4217 Currency Code", example = "USD")
        String currency
) {}
