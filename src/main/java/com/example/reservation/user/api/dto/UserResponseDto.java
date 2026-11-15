package com.example.reservation.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "User profile response")
public record UserResponseDto(
        @Schema(description = "User unique ID")
        UUID id,

        @Schema(description = "Tenant business ID")
        UUID businessId,

        @Schema(description = "First name", example = "Jane")
        String firstName,

        @Schema(description = "Last name", example = "Doe")
        String lastName,

        @Schema(description = "Email address", example = "jane.doe@example.com")
        String email,

        @Schema(description = "Phone number", example = "+1987654321")
        String phone,

        @Schema(description = "User role", example = "CUSTOMER")
        String role,

        @Schema(description = "User account status", example = "ACTIVE")
        String status,

        @Schema(description = "Profile creation timestamp")
        Instant createdAt
) {}
