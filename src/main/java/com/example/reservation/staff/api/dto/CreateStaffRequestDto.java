package com.example.reservation.staff.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Create or link staff member profile")
public record CreateStaffRequestDto(
        @Schema(description = "Business tenant ID")
        @NotNull(message = "businessId is required")
        UUID businessId,

        @Schema(description = "Associated user account ID")
        @NotNull(message = "userId is required")
        UUID userId,

        @Schema(description = "Staff job title", example = "Senior Stylist")
        @NotBlank(message = "jobTitle is required")
        String jobTitle,

        @Schema(description = "Staff bio / specialization")
        String bio
) {}

