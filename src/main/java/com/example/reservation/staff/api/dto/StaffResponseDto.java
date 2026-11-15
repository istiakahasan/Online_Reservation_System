package com.example.reservation.staff.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Staff profile details")
public record StaffResponseDto(
        @Schema(description = "Staff profile unique ID")
        UUID id,

        @Schema(description = "Business tenant ID")
        UUID businessId,

        @Schema(description = "Associated user ID")
        UUID userId,

        @Schema(description = "Staff full name", example = "Marcus Vance")
        String fullName,

        @Schema(description = "Job title", example = "Senior Stylist")
        String jobTitle,

        @Schema(description = "Staff bio / summary")
        String bio,

        @Schema(description = "Active status flag")
        Boolean isActive
) {}

