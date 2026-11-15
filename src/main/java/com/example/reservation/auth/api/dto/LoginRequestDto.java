package com.example.reservation.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "User authentication request")
public record LoginRequestDto(
        @Schema(description = "Tenant business ID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        @NotNull(message = "businessId is required")
        UUID businessId,

        @Schema(description = "User email", example = "client@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email format")
        String email,

        @Schema(description = "User password", example = "SecureP@ssw0rd123!")
        @NotBlank(message = "Password is required")
        String password
) {}
