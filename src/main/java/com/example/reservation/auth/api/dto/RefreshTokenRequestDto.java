package com.example.reservation.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token rotation request")
public record RefreshTokenRequestDto(
        @Schema(description = "Existing valid refresh token")
        @NotBlank(message = "refreshToken is required")
        String refreshToken
) {}
