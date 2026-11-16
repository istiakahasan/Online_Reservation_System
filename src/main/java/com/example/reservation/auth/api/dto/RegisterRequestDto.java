package com.example.reservation.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Customer registration request")
public record RegisterRequestDto(
        @Schema(description = "Tenant business ID", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        @NotNull(message = "businessId is required")
        UUID businessId,

        @Schema(description = "First name", example = "John")
        @NotBlank(message = "First name is required")
        String firstName,

        @Schema(description = "Last name", example = "Doe")
        @NotBlank(message = "Last name is required")
        String lastName,

        @Schema(description = "Email", example = "john.doe@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @Schema(description = "Phone number", example = "+1234567890")
        String phone,

        @Schema(description = "Password", example = "P@ssw0rd123!")
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {}
