package com.example.reservation.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Update user profile request")
public record UpdateUserRequestDto(
        @Schema(description = "First name", example = "Jane")
        @NotBlank(message = "First name is required")
        String firstName,

        @Schema(description = "Last name", example = "Doe")
        @NotBlank(message = "Last name is required")
        String lastName,

        @Schema(description = "Phone number", example = "+1987654321")
        String phone
) {}

